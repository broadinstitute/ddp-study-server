package org.broadinstitute.dsm.db.dao.ddp.abstraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;

@TableName(
        name = DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD,
        alias = DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ALIAS,
        primaryKey = DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID,
        columnPrefix = "")
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicalRecordAbstractionFieldDto {

    @ColumnName(DBConstants.MEDICAL_RECORD_ABSTRACTION_FIELD_ID)
    private final Long medicalRecordAbstractionFieldId;

    @ColumnName(DBConstants.DISPLAY_NAME)
    private final String displayName;

    @ColumnName(DBConstants.TYPE)
    private final String type;

    @ColumnName(DBConstants.ADDITIONAL_TYPE)
    private final String additionalType;

    @ColumnName(DBConstants.POSSIBLE_VALUE)
    private final String possibleValues;

    @ColumnName(DBConstants.HELP_TEXT)
    private final String helpText;

    @ColumnName(DBConstants.FILE_INFO)
    private final Boolean fileInfo;

    @ColumnName(DBConstants.MEDICAL_RECORD_ABSTRACTION_GROUP_ID)
    private final Long medicalRecordAbstractionGroupId;

    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    private final String ddpInstanceId;

    @ColumnName(DBConstants.ORDER_NUMBER)
    private final Integer orderNumber;

    @ColumnName(DBConstants.DELETED)
    private final Integer deleted;

}
