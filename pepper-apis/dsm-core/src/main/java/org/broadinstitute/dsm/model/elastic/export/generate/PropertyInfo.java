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
import org.broadinstitute.dsm.db.SomaticResultUpload;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Address;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.Files;
import org.broadinstitute.dsm.model.elastic.Invitations;
import org.broadinstitute.dsm.model.elastic.Profile;
import org.broadinstitute.dsm.model.elastic.Status;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.filter.query.ElasticSearchPropertyName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;

//class to hold property information such as profile, dsm, etc, as well as whether it is collection type or not
public class PropertyInfo {

    private static final Map<String, PropertyInfo> TABLE_ALIAS_MAPPINGS;

    static {
        TABLE_ALIAS_MAPPINGS = new HashMap<>(
                Map.of(DBConstants.DDP_MEDICAL_RECORD_ALIAS, new PropertyInfo(MedicalRecord.class, true),
                        DBConstants.DDP_TISSUE_ALIAS, new PropertyInfo(Tissue.class, true),
                        DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS, new PropertyInfo(OncHistoryDetail.class, true),
                        DBConstants.DDP_PARTICIPANT_DATA_ALIAS, new PropertyInfo(ParticipantData.class, true),
                        DBConstants.DDP_PARTICIPANT_RECORD_ALIAS, new PropertyInfo(Participant.class, false),
                        DBConstants.DDP_PARTICIPANT_ALIAS, new PropertyInfo(Participant.class, false),
                        DBConstants.DDP_ONC_HISTORY_ALIAS, new PropertyInfo(OncHistory.class, false),
                        DBConstants.SM_ID_TABLE_ALIAS, new PropertyInfo(SmId.class, true),
                        DBConstants.COHORT_ALIAS, new PropertyInfo(CohortTag.class, true),
                        DBConstants.DDP_KIT_REQUEST_ALIAS, new PropertyInfo(KitRequestShipping.class, true)
                ));
        TABLE_ALIAS_MAPPINGS.put(DBConstants.SOMATIC_DOCUMENTS_TABLE_ALIAS, new PropertyInfo(SomaticResultUpload.class, true));
        TABLE_ALIAS_MAPPINGS.put(DBConstants.DDP_KIT_ALIAS, new PropertyInfo(KitRequestShipping.class, true));
        TABLE_ALIAS_MAPPINGS.put(DBConstants.DDP_MERCURY_SEQUENCING_ALIAS, new PropertyInfo(ClinicalOrder.class, true));
        TABLE_ALIAS_MAPPINGS.put(ESObjectConstants.PROFILE, new PropertyInfo(Profile.class, false));
        TABLE_ALIAS_MAPPINGS.put(ESObjectConstants.INVITATIONS, new PropertyInfo(Invitations.class, false));
        TABLE_ALIAS_MAPPINGS.put(ESObjectConstants.ADDRESS, new PropertyInfo(Address.class, false));
        TABLE_ALIAS_MAPPINGS.put(ESObjectConstants.FILES, new PropertyInfo(Files.class, true));
        TABLE_ALIAS_MAPPINGS.put(ESObjectConstants.DSM, new PropertyInfo(Dsm.class, false));
        TABLE_ALIAS_MAPPINGS.put(ESObjectConstants.DATA, new PropertyInfo(Status.class, false));
        TABLE_ALIAS_MAPPINGS.put(ESObjectConstants.PARTICIPANT_DATA, new PropertyInfo(ParticipantData.class, true));
    }

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
        ElasticSearchPropertyName elasticSearchPropertyName = propertyClass.getAnnotation(ElasticSearchPropertyName.class);
        return Objects.isNull(elasticSearchPropertyName)
                ? Util.capitalCamelCaseToLowerCamelCase(propertyClass.getSimpleName())
                : elasticSearchPropertyName.value();
    }

    public String getPrimaryKeyAsCamelCase() {
        TableName tableName = Objects.requireNonNull(propertyClass.getAnnotation(TableName.class));
        return CamelCaseConverter.of(tableName.primaryKey()).convert();
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

    public static PropertyInfo of(String alias) {
        return TABLE_ALIAS_MAPPINGS.getOrDefault(alias, new PropertyInfo(Activities.class, true));
    }

    public static boolean hasProperty(String alias) {
        return TABLE_ALIAS_MAPPINGS.containsKey(alias);
    }
}
