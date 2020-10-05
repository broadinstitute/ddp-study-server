package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiCompositeQuestion extends SqlObject {

    @SqlUpdate("INSERT INTO composite_question (question_id, allow_multiple, add_button_template_id, "
            + "additional_item_template_id, unwrap_on_export, child_orientation_type_id)"
            + " VALUES(:questionId, :allowMultiple, :addButtonTemplateId, :additionalItemTemplateId, :unwrapOnExport,"
            + "(select orientation_type_id from orientation_type where orientation_type_code = :childOrientation))")
    int insertParent(@Bind("questionId") long compositeQuestionId, @Bind("allowMultiple") boolean allowMultiple,
                     @Bind("addButtonTemplateId") Long addButtonTemplateId, @Bind("additionalItemTemplateId") Long itemTemplateId,
                     @Bind("childOrientation") OrientationType childOrientation, @Bind("unwrapOnExport") boolean unwrapOnExport);

    default void insertChildren(long compositeQuestionId, List<Long> childQuestionIds) {
        insertChild(compositeQuestionId, childQuestionIds, IntStream.rangeClosed(0, childQuestionIds.size() - 1).boxed().collect(toList()));
    }

    @SqlBatch("INSERT INTO composite_question__question (parent_question_id, child_question_id, display_order)"
            + " VALUES(:parentQuestionId, :childQuestionId, :orderIndex)")
    void insertChild(@Bind("parentQuestionId") long parentQuestionId, @Bind("childQuestionId") List<Long> childQuestionIds,
                     @Bind("orderIndex") List<Integer> orderIdxs);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoAndChildrenDtosByActivityId")
    @RegisterConstructorMapper(value = CompositeQuestionDto.class, prefix = "p")
    @RegisterConstructorMapper(value = QuestionDto.class, prefix = "c")
    @UseRowReducer(RowReducer.class)
    List<CompositeQuestionDto> findDtosByActivityId(@Bind("activityId") long activityId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoAndChildrenDtosByActivityInstanceGuidAndStableId")
    @RegisterConstructorMapper(value = CompositeQuestionDto.class, prefix = "p")
    @RegisterConstructorMapper(value = QuestionDto.class, prefix = "c")
    @UseRowReducer(RowReducer.class)
    Optional<CompositeQuestionDto> findDtoByInstanceGuidAndStableId(
            @Bind("activityInstanceGuid") String activityInstanceGuid,
            @Bind("questionStableId") String questionStableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryParentDtoAndChildDtosByChildQuestionId")
    @RegisterConstructorMapper(value = CompositeQuestionDto.class, prefix = "p")
    @RegisterConstructorMapper(value = QuestionDto.class, prefix = "c")
    @UseRowReducer(RowReducer.class)
    Optional<CompositeQuestionDto> findParentDtoByChildQuestionId(@Bind("childQuestionId") long childQuestionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryCompositeQuestionIdByChildQuestionId")
    Optional<Long> findParentQuestionIdByChildQuestionId(@Bind("childQuestionId") long childQuestionId);

    default Optional<Long>  findParentQuestionIdByChildQuestion(QuestionDto questionDto) {
        return findParentQuestionIdByChildQuestionId(questionDto.getId());
    }

    class RowReducer implements LinkedHashMapRowReducer<Long, CompositeQuestionDto> {
        @Override
        public void accumulate(Map<Long, CompositeQuestionDto> map, RowView rowView) {
            CompositeQuestionDto parentQuestionDto = map.computeIfAbsent(rowView.getColumn("p_question_id", Long.class),
                    id -> rowView.getRow(CompositeQuestionDto.class));
            if (rowView.getColumn("c_question_id", Long.class) != null) {
                parentQuestionDto.addChildQuestion(rowView.getRow(QuestionDto.class));
            }
        }
    }

    default Map<Long, List<Long>> findOrderedChildQuestionIdsByParentIds(Set<Long> parentQuestionIds) {
        Map<Long, List<Long>> parentIdToChildIds = new HashMap<>();
        _findOrderedChildIdsByParentIds(parentQuestionIds).forEach(pair -> {
            parentIdToChildIds
                    .computeIfAbsent(pair.getParentId(), id -> new ArrayList<>())
                    .add(pair.getChildId());
        });
        return parentIdToChildIds;
    }

    @SqlQuery("select cqq.parent_question_id, cqq.child_question_id"
            + "  from composite_question__question as cqq"
            + " where cqq.parent_question_id in (<parentQuestionIds>)"
            + " order by cqq.parent_question_id asc, cqq.display_order asc")
    @RegisterConstructorMapper(IdPair.class)
    Stream<IdPair> _findOrderedChildIdsByParentIds(
            @BindList(value = "parentQuestionIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> parentQuestionIds);

    class IdPair {
        private long parentId;
        private long childId;

        @JdbiConstructor
        public IdPair(
                @ColumnName("parent_question_id") long parentId,
                @ColumnName("child_question_id") long childId) {
            this.parentId = parentId;
            this.childId = childId;
        }

        public long getParentId() {
            return parentId;
        }

        public long getChildId() {
            return childId;
        }
    }
}
