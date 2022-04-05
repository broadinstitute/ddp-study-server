package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.MatrixGroupDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

import java.util.List;

public interface JdbiMatrixGroup extends SqlObject {

    @SqlUpdate("insert into matrix_group (matrix_question_id, group_stable_id, name_template_id, display_order, revision_id)"
            + " values (:matrixQuestionId, :groupStableId, :nameTemplateId, :displayOrder, :revisionId)")
    @GetGeneratedKeys
    long insert(@Bind("matrixQuestionId") long questionId,
                @Bind("groupStableId") String stableId,
                @Bind("nameTemplateId") Long nameTemplateId,
                @Bind("displayOrder") int displayOrder,
                @Bind("revisionId") long revisionId);

    @SqlQuery("select matrix_group_id from matrix_group where matrix_question_id = :questionId and group_stable_id = :stableId")
    Long findGroupIdByCodeAndQuestionId(@Bind("questionId") long questionId,
                                        @Bind("stableId") String groupStableId);

    @SqlQuery("select group_stable_id from matrix_group where matrix_group_id = :groupId")
    String findGroupCodeById(@Bind("groupId") long groupId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryMatrixGroupsByStableIdsQuestionIdAndRevision")
    @RegisterConstructorMapper(MatrixGroupDto.class)
    List<MatrixGroupDto> findGroupsByStableIds(@Bind("questionId") long questionId,
                                               @BindList("stableIds") List<String> stableIds,
                                               @Bind("instanceGuid") String instanceGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryMatrixGroupsByIdsQuestionIdAndRevision")
    @RegisterConstructorMapper(MatrixGroupDto.class)
    List<MatrixGroupDto> findGroupsByIds(@Bind("questionId") long questionId,
                                    @BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) List<Long> ids,
                                    @Bind("instanceGuid") String instanceGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllActiveOrderedMatrixGroupsQuestionId")
    @RegisterConstructorMapper(MatrixGroupDto.class)
    List<MatrixGroupDto> findAllActiveOrderedMatrixGroupsQuestionId(@Bind("questionId") long questionId);

    @SqlBatch("update matrix_group set revision_id = :revisionId where matrix_group_id = :dto.getId")
    int[] bulkUpdateRevisionIdsByDtos(@BindMethods("dto") List<MatrixGroupDto> rows,
                                      @Bind("revisionId") long[] revisionIds);

    /**
     * Checks if stable id is already used with a matrix question, and if so, is it currently active.
     */
    default boolean isCurrentlyActive(long questionId, String stableId) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(JdbiMatrixGroup.class, "isMatrixGroupStableIdCurrentlyActive")
                .render();
        return getHandle().createQuery(query)
                .bind("questionId", questionId)
                .bind("stableId", stableId)
                .mapTo(Boolean.class)
                .findFirst()
                .isPresent();
    }
}
