package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.MatrixOptionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

import java.util.Iterator;
import java.util.List;

public interface JdbiMatrixOption extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertMatrixOption")
    @GetGeneratedKeys
    long insert(@Bind("matrixQuestionId") long matrixQuestionId,
                @Bind("stableId") String stableId,
                @Bind("optionLabelTemplateId") long optionLabelTemplateId,
                @Bind("tooltipTemplateId") Long tooltipTemplateId,
                @Bind("isExclusive") boolean isExclusive,
                @Bind("groupId") Long groupId,
                @Bind("displayOrder") int displayOrder,
                @Bind("revisionId") long revisionId);

    @UseStringTemplateSqlLocator
    @SqlBatch("insertMatrixOption")
    @GetGeneratedKeys
    long[] insert(@Bind("matrixQuestionId") long matrixQuestionId,
                @Bind("stableId") Iterator<String> stableIds,
                @Bind("optionLabelTemplateId") Iterator<Long> optionLabelTemplateIds,
                @Bind("tooltipTemplateId") Iterator<Long> tooltipTemplateIds,
                @Bind("isExclusive") Iterator<Boolean> isExclusive,
                @Bind("groupId") Iterator<Long> groupId,
                @Bind("displayOrder") Iterator<Integer> displayOrder,
                @Bind("revisionId") long revisionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllActiveOrderedMatrixOptionsByQuestionId")
    @RegisterConstructorMapper(MatrixOptionDto.class)
    List<MatrixOptionDto> findAllActiveOrderedMatrixOptionsByQuestionId(@Bind("questionId") long questionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryMatrixOptionsByStableIdsQuestionIdAndRevision")
    @RegisterConstructorMapper(MatrixOptionDto.class)
    List<MatrixOptionDto> findOptions(@BindList("stableIds") List<String> stableIds,
                                        @Bind("questionId") long questionId,
                                        @Bind("instanceGuid") String instanceGuid);

    @SqlBatch("update matrix_option set revision_id = :revisionId where matrix_option_id = :dto.getId")
    int[] bulkUpdateRevisionIdsByDtos(@BindMethods("dto") List<MatrixOptionDto> options,
                                      @Bind("revisionId") long[] revisionIds);
}
