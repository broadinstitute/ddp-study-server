package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.db.dto.validation.ValidationDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiValidation extends SqlObject {

    @SqlUpdate("insert into validation(validation_type_id,allow_save,correction_hint_template_id,revision_id)"
            + " values(:validationTypeId,:allowSave,:correctionHintTemplateId,:revisionId)")
    @GetGeneratedKeys
    long insert(@Bind("validationTypeId") long validationTypeId,
                @Bind("allowSave") boolean allowSave,
                @Bind("correctionHintTemplateId") Long correctionHintTemplateId,
                @Bind("revisionId") long revisionId);


    @SqlUpdate("update validation set revision_id = :revisionId where validation_id = :validationId")
    int updateRevisionIdById(long validationId, long revisionId);

    @SqlBatch("update validation set revision_id = :revisionId where validation_id = :dto.getId")
    int[] bulkUpdateRevisionIdsByDtos(@BindMethods("dto") List<ValidationDto> validations,
                                      @Bind("revisionId") long[] revisionIds);
}
