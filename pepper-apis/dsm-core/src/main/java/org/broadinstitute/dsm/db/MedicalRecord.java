package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.FollowUp;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.handlers.util.MedicalInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TableName(name = DBConstants.DDP_MEDICAL_RECORD, alias = DBConstants.DDP_MEDICAL_RECORD_ALIAS,
        primaryKey = DBConstants.MEDICAL_RECORD_ID, columnPrefix = "")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedicalRecord implements HasDdpInstanceId {

    public static final String SQL_SELECT_MEDICAL_RECORD = "SELECT p.ddp_participant_id, p.ddp_instance_id, "
            + "inst.institution_id, inst.ddp_institution_id, inst.type, inst.participant_id, "
            + "m.medical_record_id, m.name, m.contact, m.phone, m.fax, m.fax_sent, m.fax_sent_by, m.fax_confirmed, m.fax_sent_2, "
            + "m.fax_sent_2_by, "
            + "m.fax_confirmed_2, m.fax_sent_3, m.fax_sent_3_by, m.fax_confirmed_3, m.mr_received, m.follow_ups, m.mr_document, "
            + "m.mr_document_file_names, m.no_action_needed, "
            + "m.mr_problem, m.mr_problem_text, m.unable_obtain, m.unable_obtain_text, m.duplicate, m.followup_required, "
            + "m.followup_required_text, m.international, m.cr_required, m.pathology_present, "
            + "m.notes, m.additional_values_json, (SELECT sum(log.comments is null and log.type = \"DATA_REVIEW\") as reviewMedicalRecord"
            + " FROM ddp_medical_record rec2 LEFT JOIN ddp_medical_record_log log on (rec2.medical_record_id = log.medical_record_id) "
            + "WHERE rec2.institution_id = inst.institution_id) as reviewMedicalRecord "
            + "FROM ddp_institution inst LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
            + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "LEFT JOIN ddp_medical_record as m on (m.institution_id = inst.institution_id) "
            + "WHERE ddp.instance_name = ? AND inst.type != 'NOT_SPECIFIED' AND NOT m.deleted <=> 1 ";
    public static final String SQL_SELECT_MEDICAL_RECORD_LAST_CHANGED = "SELECT m.last_changed FROM ddp_institution inst "
            + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
            + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "LEFT JOIN ddp_medical_record as m on (m.institution_id = inst.institution_id) WHERE p.participant_id = ?";
    public static final String SQL_ORDER_BY = " ORDER BY p.ddp_participant_id, inst.ddp_institution_id ASC";
    private static final Logger logger = LoggerFactory.getLogger(MedicalRecord.class);
    public static final String AND_DDP_PARTICIPANT_ID = " AND p.ddp_participant_id = '%s'";
    public static final String AND_P_DDP_PARTICIPANT_ID_IN = " AND p.ddp_participant_id IN (?)";
    public static final String QUESTION_MARK = "?";
    @ColumnName(DBConstants.MEDICAL_RECORD_ID)
    private long medicalRecordId;
    @ColumnName(DBConstants.INSTITUTION_ID)
    private long institutionId;
    @ColumnName(DBConstants.DDP_INSTITUTION_ID)
    private String ddpInstitutionId;
    @ColumnName(DBConstants.DDP_PARTICIPANT_ID)
    private String ddpParticipantId;
    @ColumnName(DBConstants.TYPE)
    private String type;
    @ColumnName(DBConstants.NAME)
    private String name;
    @ColumnName(DBConstants.CONTACT)
    private String contact;
    @ColumnName(DBConstants.PHONE)
    private String phone;
    @ColumnName(DBConstants.FAX)
    private String fax;
    @ColumnName(DBConstants.FAX_SENT)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String faxSent;
    @ColumnName(DBConstants.FAX_SENT_BY)
    private String faxSentBy;
    @ColumnName(DBConstants.FAX_CONFIRMED)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String faxConfirmed;
    @ColumnName(DBConstants.FAX_SENT_2)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String faxSent2;
    @ColumnName(DBConstants.FAX_SENT_2_BY)
    private String faxSent2By;
    @ColumnName(DBConstants.FAX_CONFIRMED_2)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String faxConfirmed2;
    @ColumnName(DBConstants.FAX_SENT_3)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String faxSent3;
    @ColumnName(DBConstants.FAX_SENT_3_BY)
    private String faxSent3By;
    @ColumnName(DBConstants.FAX_CONFIRMED_3)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String faxConfirmed3;
    @ColumnName(DBConstants.MR_RECEIVED)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String mrReceived;
    @ColumnName(DBConstants.MR_DOCUMENT)
    private String mrDocument;
    @ColumnName(DBConstants.MR_DOCUMENT_FILE_NAMES)
    private String mrDocumentFileNames;
    @ColumnName(DBConstants.MR_PROBLEM)
    private boolean mrProblem;
    @ColumnName(DBConstants.MR_PROBLEM_TEXT)
    private String mrProblemText;
    @ColumnName(DBConstants.MR_UNABLE_OBTAIN)
    private boolean unableObtain;
    @ColumnName(DBConstants.MR_UNABLE_OBTAIN_TEXT)
    private String unableObtainText;
    @ColumnName(DBConstants.FOLLOWUP_REQUIRED)
    private boolean followupRequired;
    @ColumnName(DBConstants.FOLLOWUP_REQUIRED_TEXT)
    private String followupRequiredText;
    @ColumnName(DBConstants.DUPLICATE)
    private boolean duplicate;
    @ColumnName(DBConstants.INTERNATIONAL)
    private boolean international;
    @ColumnName(DBConstants.CR_REQUIRED)
    private boolean crRequired;

    @ColumnName(DBConstants.NO_ACTION_NEEDED)
    private boolean noActionNeeded;
    @ColumnName(DBConstants.NOTES)
    private String notes;
    @ColumnName(DBConstants.FOLLOW_UP_REQUESTS)
    private FollowUp[] followUps;
    @ColumnName(DBConstants.ADDITIONAL_VALUES_JSON)
    @JsonProperty("dynamicFields")
    @SerializedName("dynamicFields")
    private String additionalValuesJson;
    @ColumnName(DBConstants.REVIEW_MEDICAL_RECORD)
    private boolean reviewMedicalRecord;
    @ColumnName(DBConstants.PATHOLOGY_PRESENT)
    private String pathologyPresent;
    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    private long ddpInstanceId;
    @ColumnName(DBConstants.DELETED)
    private Boolean deleted;


    public MedicalRecord(long medicalRecordId, long institutionId) {
        this.medicalRecordId = medicalRecordId;
        this.institutionId = institutionId;
    }

    public MedicalRecord() {
    }

    public MedicalRecord(long ddpInstanceId) {
        this.ddpInstanceId = ddpInstanceId;
    }

    public MedicalRecord(long medicalRecordId, long institutionId, String ddpInstitutionId, String type, String name, String contact,
                         String phone, String fax, String faxSent, String faxSentBy, String faxConfirmed, String faxSent2,
                         String faxSent2By, String faxConfirmed2, String faxSent3, String faxSent3By, String faxConfirmed3,
                         String mrReceived, String mrDocument, String mrDocumentFileNames, boolean mrProblem, String mrProblemText,
                         boolean unableObtain, boolean duplicate, boolean international, boolean crRequired, String pathologyPresent,
                         String notes, boolean reviewMedicalRecord, FollowUp[] followUps, boolean followUpRequired,
                         String followupRequiredText, String additionalValuesJson, String unableObtainText, String ddpParticipantId,
                         long ddpInstanceId, boolean noActionNeeded) {
        this.medicalRecordId = medicalRecordId;
        this.institutionId = institutionId;
        this.ddpInstitutionId = ddpInstitutionId;
        this.type = type;
        this.name = name;
        this.contact = contact;
        this.phone = phone;
        this.fax = fax;
        this.faxSent = faxSent;
        this.faxSentBy = faxSentBy;
        this.faxConfirmed = faxConfirmed;
        this.faxSent2 = faxSent2;
        this.faxSent2By = faxSent2By;
        this.faxConfirmed2 = faxConfirmed2;
        this.faxSent3 = faxSent3;
        this.faxSent3By = faxSent3By;
        this.faxConfirmed3 = faxConfirmed3;
        this.mrReceived = mrReceived;
        this.mrDocument = mrDocument;
        this.mrDocumentFileNames = mrDocumentFileNames;
        this.mrProblem = mrProblem;
        this.mrProblemText = mrProblemText;
        this.unableObtain = unableObtain;
        this.duplicate = duplicate;
        this.international = international;
        this.crRequired = crRequired;
        this.pathologyPresent = pathologyPresent;
        this.notes = notes;
        this.reviewMedicalRecord = reviewMedicalRecord;
        this.followUps = followUps;
        this.followupRequired = followUpRequired;
        this.followupRequiredText = followupRequiredText;
        this.additionalValuesJson = additionalValuesJson;
        this.unableObtainText = unableObtainText;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
        this.noActionNeeded = noActionNeeded;
    }

    public static MedicalRecord getMedicalRecord(@NonNull ResultSet rs) throws SQLException {
        MedicalRecord medicalRecord = new MedicalRecord(rs.getLong(DBConstants.MEDICAL_RECORD_ID), rs.getLong(DBConstants.INSTITUTION_ID),
                rs.getString(DBConstants.DDP_INSTITUTION_ID), rs.getString(DBConstants.TYPE), rs.getString(DBConstants.NAME),
                rs.getString(DBConstants.CONTACT), rs.getString(DBConstants.PHONE), rs.getString(DBConstants.FAX),
                rs.getString(DBConstants.FAX_SENT), rs.getString(DBConstants.FAX_SENT_BY), rs.getString(DBConstants.FAX_CONFIRMED),
                rs.getString(DBConstants.FAX_SENT_2), rs.getString(DBConstants.FAX_SENT_2_BY), rs.getString(DBConstants.FAX_CONFIRMED_2),
                rs.getString(DBConstants.FAX_SENT_3), rs.getString(DBConstants.FAX_SENT_3_BY), rs.getString(DBConstants.FAX_CONFIRMED_3),
                rs.getString(DBConstants.MR_RECEIVED), rs.getString(DBConstants.MR_DOCUMENT),
                rs.getString(DBConstants.MR_DOCUMENT_FILE_NAMES), rs.getBoolean(DBConstants.MR_PROBLEM),
                rs.getString(DBConstants.MR_PROBLEM_TEXT), rs.getBoolean(DBConstants.MR_UNABLE_OBTAIN),
                rs.getBoolean(DBConstants.DUPLICATE), rs.getBoolean(DBConstants.INTERNATIONAL), rs.getBoolean(DBConstants.CR_REQUIRED),
                rs.getString(DBConstants.PATHOLOGY_PRESENT), rs.getString(DBConstants.NOTES),
                rs.getBoolean(DBConstants.REVIEW_MEDICAL_RECORD),
                new Gson().fromJson(rs.getString(DBConstants.FOLLOW_UP_REQUESTS), FollowUp[].class),
                rs.getBoolean(DBConstants.FOLLOWUP_REQUIRED), rs.getString(DBConstants.FOLLOWUP_REQUIRED_TEXT),
                rs.getString(DBConstants.ADDITIONAL_VALUES_JSON), rs.getString(DBConstants.MR_UNABLE_OBTAIN_TEXT),
                rs.getString(DBConstants.DDP_PARTICIPANT_ID), rs.getLong(DBConstants.DDP_INSTANCE_ID),
                rs.getBoolean(DBConstants.NO_ACTION_NEEDED));
        return medicalRecord;
    }

    public static MedicalRecord getMedicalRecord(@NonNull String realm, @NonNull String ddpParticipantId, @NonNull String medicalRecordId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    SQL_SELECT_MEDICAL_RECORD + QueryExtension.BY_DDP_PARTICIPANT_ID + QueryExtension.BY_MEDICAL_RECORD_ID)) {
                stmt.setString(1, realm);
                stmt.setString(2, ddpParticipantId);
                stmt.setString(3, medicalRecordId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getMedicalRecord(rs);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting medicalRecord " + medicalRecordId + " of participant " + ddpParticipantId,
                    results.resultException);
        }

        return (MedicalRecord) results.resultValue;
    }

    public static Map<String, List<MedicalRecord>> getMedicalRecords(@NonNull String realm) {
        return getMedicalRecords(realm, null);
    }

    public static Map<String, List<MedicalRecord>> getMedicalRecords(@NonNull String realm, String queryAddition) {
        logger.info("Collection mr information");
        Map<String, List<MedicalRecord>> medicalRecords = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    DBUtil.getFinalQuery(SQL_SELECT_MEDICAL_RECORD, queryAddition) + SQL_ORDER_BY)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        List<MedicalRecord> medicalRecordList = new ArrayList<>();
                        if (medicalRecords.containsKey(ddpParticipantId)) {
                            medicalRecordList = medicalRecords.get(ddpParticipantId);
                        } else {
                            medicalRecords.put(ddpParticipantId, medicalRecordList);
                        }
                        medicalRecordList.add(getMedicalRecord(rs));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of medicalRecords ", results.resultException);
        }
        logger.info("Got " + medicalRecords.size() + " participants medicalRecords in DSM DB for " + realm);
        return medicalRecords;
    }

    public static List<MedicalRecord> getMedicalRecordsByInstanceNameAndDdpParticipantId(String instanceName, String ddpParticipantId) {
        return getMedicalRecords(instanceName, String.format(AND_DDP_PARTICIPANT_ID, ddpParticipantId))
                .getOrDefault(ddpParticipantId, new ArrayList<>());
    }

    public static Map<String, List<MedicalRecord>> getMedicalRecordsByParticipantIds(@NonNull String realm, List<String> participantIds) {
        String queryAddition = AND_P_DDP_PARTICIPANT_ID_IN.replace(QUESTION_MARK, DBUtil.participantIdsInClause(participantIds));
        return getMedicalRecords(realm, queryAddition);
    }

    public static MedicalInfo getDDPInstitutionInfo(@NonNull DDPInstance ddpInstance, @NonNull String ddpParticipantId) {
        String dsmRequest =
                ddpInstance.getBaseUrl() + RoutePath.DDP_INSTITUTION_PATH.replace(RequestParameter.PARTICIPANTID, ddpParticipantId);
        try {
            MedicalInfo medicalInfo =
                    DDPRequestUtil.getResponseObject(MedicalInfo.class, dsmRequest, ddpInstance.getName(), ddpInstance.isHasAuth0Token());
            return medicalInfo;
        } catch (IOException e) {
            throw new RuntimeException("Couldn't get participants and institutions for ddpInstance " + ddpInstance.getName(), e);
        }
    }

    @JsonProperty("followupRequired")
    public boolean isFollowupRequired() {
        return followupRequired;
    }

    @JsonProperty("followupRequiredText")
    public String getFollowupRequiredText() {
        return followupRequiredText;
    }

    @JsonProperty("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        return ObjectMapperSingleton.readValue(additionalValuesJson, new TypeReference<Map<String, Object>>() {
        });
    }

    @Override
    public Optional<Long> extractDdpInstanceId() {
        return Optional.of(getDdpInstanceId());
    }
}
