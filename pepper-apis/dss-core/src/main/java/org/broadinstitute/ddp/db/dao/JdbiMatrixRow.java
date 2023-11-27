package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.MatrixRowDto;
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

import java.util.Iterator;
import java.util.List;

public interface JdbiMatrixRow extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertMatrixRow")
    @GetGeneratedKeys
    long insert(@Bind("matrixQuestionId") long matrixQuestionId,
                @Bind("stableId") String stableId,
                @Bind("questionLabelTemplateId") long questionLabelTemplateId,
                @Bind("tooltipTemplateId") Long tooltipTemplateId,
                @Bind("displayOrder") int displayOrder,
                @Bind("revisionId") long revisionId);

    @UseStringTemplateSqlLocator
    @SqlBatch("insertMatrixRow")
    @GetGeneratedKeys
    long[] insert(@Bind("matrixQuestionId") long matrixQuestionId,
                @Bind("stableId") Iterator<String> stableIds,
                @Bind("questionLabelTemplateId") Iterator<Long> questionLabelTemplateIds,
                @Bind("tooltipTemplateId") Iterator<Long> tooltipTemplateIds,
                @Bind("displayOrder") Iterator<Integer> displayOrder,
                @Bind("revisionId") long revisionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllActiveOrderedMatrixRowsByQuestionId")
    @RegisterConstructorMapper(MatrixRowDto.class)
    List<MatrixRowDto> findAllActiveOrderedMatrixRowsByQuestionId(@Bind("questionId") long questionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryMatrixRowsByStableIdsQuestionIdAndRevision")
    @RegisterConstructorMapper(MatrixRowDto.class)
    List<MatrixRowDto> findRows(@Bind("questionId") long questionId,
                                @BindList("stableIds") List<String> stableIds,
                                @Bind("instanceGuid") String instanceGuid);

    @SqlBatch("update matrix_row set revision_id = :revisionId where matrix_row_id = :dto.getId")
    int[] bulkUpdateRevisionIdsByDtos(@BindMethods("dto") List<MatrixRowDto> rows,
                                      @Bind("revisionId") long[] revisionIds);

    /**
     * Checks if stable id is already used with a matrix question, and if so, is it currently active.
     */
    default boolean isCurrentlyActive(long questionId, String stableId) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(JdbiMatrixRow.class, "isMatrixRowStableIdCurrentlyActive")
                .render();
        return getHandle().createQuery(query)
                .bind("questionId", questionId)
                .bind("stableId", stableId)
                .mapTo(Boolean.class)
                .findFirst()
                .isPresent();
    }
}
