package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.ClinicalOrder;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.statics.DBConstants;

public class PropertyInfo {

    public static final Map<String, PropertyInfo> TABLE_ALIAS_MAPPINGS = new HashMap<>(
            Map.ofEntries(
                    Map.entry(DBConstants.DDP_MEDICAL_RECORD_ALIAS, new PropertyInfo(MedicalRecord.class, true)),
                    Map.entry(DBConstants.DDP_TISSUE_ALIAS, new PropertyInfo(Tissue.class, true)),
                    Map.entry(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS, new PropertyInfo(OncHistoryDetail.class, true)),
                    Map.entry(DBConstants.DDP_PARTICIPANT_DATA_ALIAS, new PropertyInfo(ParticipantData.class, true)),
                    Map.entry(DBConstants.DDP_PARTICIPANT_RECORD_ALIAS, new PropertyInfo(Participant.class, false)),
                    Map.entry(DBConstants.DDP_PARTICIPANT_ALIAS, new PropertyInfo(Participant.class, false)),
                    Map.entry(DBConstants.DDP_ONC_HISTORY_ALIAS, new PropertyInfo(OncHistory.class, false)),
                    Map.entry(DBConstants.SM_ID_TABLE_ALIAS, new PropertyInfo(SmId.class, true)),
                    Map.entry(DBConstants.COHORT_ALIAS, new PropertyInfo(CohortTag.class, true)),
                    Map.entry(DBConstants.DDP_KIT_REQUEST_ALIAS, new PropertyInfo(KitRequestShipping.class, true)),
                    Map.entry(DBConstants.DDP_MERCURY_SEQUENCING_ALIAS, new PropertyInfo(ClinicalOrder.class, true))));

    private Class<?> propertyClass;
    private boolean isCollection;
    private String fieldName;


    public PropertyInfo(Class<?> propertyClass, boolean isCollection) {
        this.propertyClass = Objects.requireNonNull(propertyClass);
        this.isCollection = isCollection;
    }

    public void setIsCollection(boolean isCollection) {
        this.isCollection = isCollection;
    }

    public String getPropertyName() {
        return Util.capitalCamelCaseToLowerCamelCase(propertyClass.getSimpleName());
    }

    public String getPrimaryKeyAsCamelCase() {
        TableName tableName = Objects.requireNonNull(propertyClass.getAnnotation(TableName.class));
        return Util.underscoresToCamelCase(tableName.primaryKey());
    }

    public boolean isCollection() {
        return isCollection;
    }

    public String getFieldName() {
        if (StringUtils.isBlank(this.fieldName)) {
            this.fieldName = StringUtils.EMPTY;
        }
        return this.fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName);
    }

    public Class<?> getPropertyClass() {
        return propertyClass;
    }
}
