package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.types.OrientationType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiCompositeQuestion extends SqlObject {

    @SqlUpdate("INSERT INTO composite_question (question_id, allow_multiple, add_button_template_id, "
            + "additional_item_template_id, unwrap_on_export, child_orientation_type_id)"
            + " VALUES(:questionId, :allowMultiple, :addButtonTemplateId, :additionaItemTemplateId, :unwrapOnExport,"
            + "(select orientation_type_id from orientation_type where orientation_type_code = :childOrientation))")
    int insertParent(@Bind("questionId") long compositeQuestionId, @Bind("allowMultiple") boolean allowMultiple,
                     @Bind("addButtonTemplateId") Long addButtonTemplateId, @Bind("additionaItemTemplateId") Long itemTemplateId,
                     @Bind("childOrientation") OrientationType childOrientation, @Bind("unwrapOnExport") boolean unwrapOnExport);

    default void insertChildren(long compositeQuestionId, List<Long> childQuestionIds) {
        insertChild(compositeQuestionId, childQuestionIds, IntStream.rangeClosed(0, childQuestionIds.size() - 1).boxed().collect(toList()));
    }

    @SqlBatch("INSERT INTO composite_question__question (parent_question_id, child_question_id, display_order)"
            + " VALUES(:parentQuestionId, :childQuestionId, :orderIndex)")
    void insertChild(@Bind("parentQuestionId") long parentQuestionId, @Bind("childQuestionId") List<Long> childQuestionIds,
                     @Bind("orderIndex") List<Integer> orderIdxs);


    @UseStringTemplateSqlLocator
    @SqlQuery("queryDtoAndChildrenDtosByQuestionId")
    @RegisterConstructorMapper(value = CompositeQuestionDto.class, prefix = "p")
    @RegisterConstructorMapper(value = QuestionDto.class, prefix = "c")
    @UseRowReducer(RowReducer.class)
    Optional<CompositeQuestionDto> findDtoByQuestionId(@Bind("questionId") long questionId);

    default Optional<CompositeQuestionDto> findDtoByQuestion(QuestionDto questionDto) {
        return findDtoByQuestionId(questionDto.getId());
    }

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

    // NOT USED
    class RowReducer implements LinkedHashMapRowReducer<Long, CompositeQuestionDto> {
        @Override
        public void accumulate(Map<Long, CompositeQuestionDto> map, RowView rowView) {
            CompositeQuestionDto parentQuestionDto = map.computeIfAbsent(rowView.getColumn("p_question_id", Long.class),
                    id -> rowView.getRow(CompositeQuestionDto.class));
            if (rowView.getColumn("c_question_id", Long.class) != null) {
                // parentQuestionDto.addChildQuestion(rowView.getRow(QuestionDto.class));
            }
        }
    }
}
