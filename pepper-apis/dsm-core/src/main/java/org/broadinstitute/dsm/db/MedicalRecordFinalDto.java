
package org.broadinstitute.dsm.db;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(name = DBConstants.DDP_MEDICAL_RECORD_FINAL, alias = DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS,
        primaryKey = DBConstants.DDP_MEDICAL_RECORD_FINAL_ID, columnPrefix = "")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedicalRecordFinalDto {

    @ColumnName(DBConstants.DDP_MEDICAL_RECORD_FINAL_ID)
    private final Long medicalRecordFinalId;

    @ColumnName(DBConstants.PARTICIPANT_ID)
    private final Long participantId;

    @ColumnName(DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID)
    private final Long medicalRecordAbstractionFieldId;

    @ColumnName(DBConstants.VALUE)
    private final String value;

    @ColumnName(DBConstants.NO_DATA)
    private final Long noData;

    @ColumnName(DBConstants.DATA_RELEASE_ID)
    private final Long dataReleaseId;

}

