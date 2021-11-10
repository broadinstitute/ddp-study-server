package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.MatrixGroupDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

import java.util.List;

public interface JdbiMatrixGroup extends SqlObject {

    @SqlUpdate("insert into matrix_group (matrix_question_id, group_stable_id, name_template_id, display_order, revision_id)"
            + " values (:matrixQuestionId, :groupStableId, :nameTemplateId, :displayOrder, :revisionId)")
    @GetGeneratedKeys
    long insert(@Bind("matrixQuestionId") long questionId,
                @Bind("groupStableId") String stableId,
                @Bind("nameTemplateId") long nameTemplateId,
                @Bind("displayOrder") int displayOrder,
                @Bind("revisionId") long revisionId);

    @SqlQuery("select matrix_group_id from matrix_group where group_stable_id = :stableId")
    Long findGroupIdByCode(@Bind("stableId") String groupStableId);


    @UseStringTemplateSqlLocator
    @SqlQuery("queryMatrixGroupsByStableIdsQuestionIdAndRevision")
    @RegisterConstructorMapper(MatrixGroupDto.class)
    List<MatrixGroupDto> findGroups(@BindList("stableIds") List<String> stableIds,
                                    @Bind("questionId") long questionId,
                                    @Bind("instanceGuid") String instanceGuid);
}
