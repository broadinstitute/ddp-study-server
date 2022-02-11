package org.broadinstitute.dsm.db;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
@TableName(
        name = DBConstants.DDP_ONC_HISTORY_DETAIL,
        alias = DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS,
        primaryKey = DBConstants.ONC_HISTORY_DETAIL_ID,
        columnPrefix = "")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OncHistoryDetail {

    private static final Logger logger = LoggerFactory.getLogger(OncHistoryDetail.class);

    public static final String SQL_SELECT_ONC_HISTORY_DETAIL = "SELECT p.ddp_participant_id, p.participant_id, oD.onc_history_detail_id, oD.request, oD.deleted, oD.fax_sent, oD.tissue_received, oD.medical_record_id, oD.date_px, oD.type_px, "
            + "oD.location_px, oD.histology, oD.accession_number, oD.facility, oD.phone, oD.fax, oD.notes, oD.additional_values_json, "
            + "oD.request, oD.fax_sent, oD.fax_sent_by, oD.fax_confirmed, oD.fax_sent_2, oD.fax_sent_2_by, oD.fax_confirmed_2, oD.fax_sent_3, "
            + "oD.fax_sent_3_by, oD.fax_confirmed_3, oD.tissue_received, oD.tissue_problem_option, oD.gender, oD.destruction_policy, oD.unable_obtain_tissue, "
            + "t.tissue_id, t.notes, count_received, tissue_type, tissue_site, tumor_type, h_e, pathology_report, collaborator_sample_id, block_sent, scrolls_received, sk_id, sm_id, "
            + "sent_gp, first_sm_id, additional_tissue_value_json, expected_return, return_date, return_fedex_id, shl_work_number, tumor_percentage, tissue_sequence, "
            + " scrolls_count, uss_count, h_e_count, blocks_count, sm.sm_id_value, sm.sm_id_type_id, sm.sm_id_pk, sm.deleted, sm.tissue_id, smt.sm_id_type "
            + "FROM ddp_onc_history_detail oD "
            + "LEFT JOIN ddp_medical_record m on (oD.medical_record_id = m.medical_record_id AND NOT oD.deleted <=> 1 AND NOT m.deleted <=> 1) "
            + "LEFT JOIN ddp_institution inst on (inst.institution_id = m.institution_id) "
            + "LEFT JOIN ddp_participant p on (p.participant_id = inst.participant_id) "
            + "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) "
            + "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id AND NOT t.deleted <=> 1) "
            + "LEFT JOIN sm_id sm on (sm.tissue_id = t.tissue_id AND NOT sm.deleted <=> 1 ) "
            + "LEFT JOIN sm_id_type smt on (smt.sm_id_type_id = sm.sm_id_type_id ) "
            + "WHERE realm.instance_name = ? ";
    private static final String SQL_CREATE_ONC_HISTORY = "INSERT INTO ddp_onc_history_detail SET medical_record_id = ?, request = ?, last_changed = ?, changed_by = ?";
    private static final String SQL_SELECT_ONC_HISTORY = "SELECT onc_history_detail_id, medical_record_id, date_px, type_px, location_px, histology, accession_number, facility,"
            + " phone, fax, notes, additional_values_json, request, fax_sent, fax_sent_by, fax_confirmed, fax_sent_2, fax_sent_2_by, fax_confirmed_2, fax_sent_3, fax_sent_3_by, fax_confirmed_3,"
            + " tissue_received, gender, tissue_problem_option, destruction_policy FROM ddp_onc_history_detail WHERE NOT (deleted <=> 1)";
    private static final String SQL_SELECT_TISSUE_RECEIVED = "SELECT tissue_received FROM ddp_onc_history_detail WHERE onc_history_detail_id = ?";
    private static final String SQL_INSERT_ONC_HISTORY_DETAIL = "INSERT INTO ddp_onc_history_detail SET medical_record_id = ?, request = ?, last_changed = ?, changed_by = ?";
    public static final String SQL_ORDER_BY = " ORDER BY p.ddp_participant_id, inst.ddp_institution_id, oD.onc_history_detail_id, t.tissue_id ASC";
    public static final String SQL_SELECT_ONC_HISTORY_LAST_CHANGED = "SELECT oD.last_changed FROM ddp_institution inst "
            + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "LEFT JOIN ddp_medical_record as m on (m.institution_id = inst.institution_id AND NOT m.deleted <=> 1) LEFT JOIN ddp_onc_history_detail as oD on (m.medical_record_id = oD.medical_record_id) "
            + "WHERE p.participant_id = ?";

    public static final String STATUS_REVIEW = "review";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_RECEIVED = "received";
    public static final String STATUS_HOLD = "hold";
    public static final String STATUS_DO_NOT_REQUEST = "no";
    public static final String STATUS_RETURNED = "returned";
    public static final String STATUS_REQUEST = "request";
    public static final String STATUS_UNABLE_TO_OBTAIN = "unableToObtain";
    public static final String PROBLEM_INSUFFICIENT_PATH = "insufficientPath";
    public static final String PROBLEM_INSUFFICIENT_SHL = "insufficientSHL";
    public static final String PROBLEM_NO_E_SIGN = "noESign";
    public static final String PROBLEM_PATH_POLICY = "pathPolicy";
    public static final String PROBLEM_PATH_NO_LOCATE = "pathNoLocate";
    public static final String PROBLEM_DESTROYED = "destroyed";
    public static final String PROBLEM_OTHER = "other";
    public static final String PROBLEM_OTHER_OLD = "Other";

    @ColumnName(DBConstants.ONC_HISTORY_DETAIL_ID)
    private long oncHistoryDetailId;

    @ColumnName(DBConstants.MEDICAL_RECORD_ID)
    private long medicalRecordId;

    @ColumnName(DBConstants.DATE_PX)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String datePx;

    @ColumnName(DBConstants.TYPE_PX)
    private String typePx;

    @ColumnName(DBConstants.LOCATION_PX)
    private String locationPx;

    @ColumnName(DBConstants.HISTOLOGY)
    private String histology;

    @ColumnName(DBConstants.ACCESSION_NUMBER)
    private String accessionNumber;

    @ColumnName(DBConstants.FACILITY)
    private String facility;

    @ColumnName(DBConstants.PHONE)
    private String phone;

    @ColumnName(DBConstants.FAX)
    private String fax;

    @ColumnName(DBConstants.NOTES)
    private String notes;

    @ColumnName(DBConstants.REQUEST)
    private String request;

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

    @ColumnName(DBConstants.TISSUE_RECEIVED)
    @DbDateConversion(SqlDateConverter.STRING_DAY)
    private String tissueReceived;

    @ColumnName(DBConstants.TISSUE_PROBLEM_OPTION)
    private String tissueProblemOption;

    @ColumnName(DBConstants.GENDER)
    private String gender;

    @ColumnName(DBConstants.ADDITIONAL_VALUES_JSON)
    @JsonProperty("dynamicFields")
    @SerializedName("dynamicFields")
    private String additionalValuesJson;

    @JsonProperty("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        try {
            return ObjectMapperSingleton.instance().readValue(additionalValuesJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException | NullPointerException e) {
            return Map.of();
        }
    }

    @ColumnName(DBConstants.DESTRUCTION_POLICY)
    private String destructionPolicy;

    private String changedBy;

    @ColumnName(DBConstants.DELETED)
    private boolean deleted;

    @ColumnName(DBConstants.UNABLE_OBTAIN_TISSUE)
    private boolean unableObtainTissue;

    private String participantId;
    private String ddpParticipantId;

    private List<Tissue> tissues;

    public OncHistoryDetail() {
    }

    public OncHistoryDetail(long oncHistoryDetailId, long medicalRecordId, String datePx, String typePx,
                            String locationPx, String histology, String accessionNumber, String facility, String phone,
                            String fax, String notes, String request, String faxSent,
                            String faxSentBy, String faxConfirmed,
                            String faxSent2, String faxSent2By, String faxConfirmed2,
                            String faxSent3, String faxSent3By, String faxConfirmed3,
                            String tissueReceived, String gender, String additionalValuesJson,
                            String tissueProblemOption, String destructionPolicy, boolean unableObtainTissue) {
        this.oncHistoryDetailId = oncHistoryDetailId;
        this.medicalRecordId = medicalRecordId;
        this.datePx = datePx;
        this.typePx = typePx;
        this.locationPx = locationPx;
        this.histology = histology;
        this.accessionNumber = accessionNumber;
        this.facility = facility;
        this.phone = phone;
        this.fax = fax;
        this.notes = notes;
        this.request = request;
        this.faxSent = faxSent;
        this.faxSentBy = faxSentBy;
        this.faxConfirmed = faxConfirmed;
        this.faxSent2 = faxSent2;
        this.faxSent2By = faxSent2By;
        this.faxConfirmed2 = faxConfirmed2;
        this.faxSent3 = faxSent3;
        this.faxSent3By = faxSent3By;
        this.faxConfirmed3 = faxConfirmed3;
        this.tissueReceived = tissueReceived;
        this.gender = gender;
        this.additionalValuesJson = additionalValuesJson;
        this.tissues = new ArrayList<>();
        this.tissueProblemOption = tissueProblemOption;
        this.destructionPolicy = destructionPolicy;
        this.unableObtainTissue = unableObtainTissue;
    }

    public OncHistoryDetail(long oncHistoryDetailId, long medicalRecordId, String datePx, String typePx,
                            String locationPx, String histology, String accessionNumber, String facility, String phone,
                            String fax, String notes, String request, String faxSent,
                            String faxSentBy, String faxConfirmed,
                            String faxSent2, String faxSent2By, String faxConfirmed2,
                            String faxSent3, String faxSent3By, String faxConfirmed3,
                            String tissueReceived, String gender, String additionalValuesJson, List<Tissue> tissues,
                            String tissueProblemOption, String destructionPolicy, boolean unableObtainTissue, String participantId, String ddpParticipantId) {
        this.oncHistoryDetailId = oncHistoryDetailId;
        this.medicalRecordId = medicalRecordId;
        this.datePx = datePx;
        this.typePx = typePx;
        this.locationPx = locationPx;
        this.histology = histology;
        this.accessionNumber = accessionNumber;
        this.facility = facility;
        this.phone = phone;
        this.fax = fax;
        this.notes = notes;
        this.request = request;
        this.faxSent = faxSent;
        this.faxSentBy = faxSentBy;
        this.faxConfirmed = faxConfirmed;
        this.faxSent2 = faxSent2;
        this.faxSent2By = faxSent2By;
        this.faxConfirmed2 = faxConfirmed2;
        this.faxSent3 = faxSent3;
        this.faxSent3By = faxSent3By;
        this.faxConfirmed3 = faxConfirmed3;
        this.tissueReceived = tissueReceived;
        this.gender = gender;
        this.additionalValuesJson = additionalValuesJson;
        this.tissues = tissues;
        this.tissueProblemOption = tissueProblemOption;
        this.destructionPolicy = destructionPolicy;
        this.unableObtainTissue = unableObtainTissue;
        this.participantId = participantId;
        this.ddpParticipantId = ddpParticipantId;
    }

    public static OncHistoryDetail getOncHistoryDetail(@NonNull ResultSet rs) throws SQLException {
        List tissues = new ArrayList<>();
        OncHistoryDetail oncHistoryDetail = new OncHistoryDetail(
                rs.getLong(DBConstants.ONC_HISTORY_DETAIL_ID),
                rs.getLong(DBConstants.MEDICAL_RECORD_ID),
                rs.getString(DBConstants.DATE_PX),
                rs.getString(DBConstants.TYPE_PX),
                rs.getString(DBConstants.LOCATION_PX),
                rs.getString(DBConstants.HISTOLOGY),
                rs.getString(DBConstants.ACCESSION_NUMBER),
                rs.getString(DBConstants.FACILITY),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.PHONE),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.NOTES),
                rs.getString(DBConstants.REQUEST),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_BY),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_CONFIRMED),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_2),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_2_BY),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_CONFIRMED_2),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_3),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_SENT_3_BY),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.FAX_CONFIRMED_3),
                rs.getString(DBConstants.TISSUE_RECEIVED),
                rs.getString(DBConstants.GENDER),
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.ADDITIONAL_VALUES_JSON), tissues,
                rs.getString(DBConstants.TISSUE_PROBLEM_OPTION),
                rs.getString(DBConstants.DESTRUCTION_POLICY),
                rs.getBoolean(DBConstants.UNABLE_OBTAIN_TISSUE),
                rs.getString(DBConstants.PARTICIPANT_ID),
                rs.getString(DBConstants.DDP_PARTICIPANT_ID)
        );
        return oncHistoryDetail;
    }

    public void addTissue(Tissue tissue) {
        if (tissues != null) {
            tissues.add(tissue);
        }
    }

    public List<Tissue> getTissues() {
        if (tissues == null) {
            tissues = new ArrayList<>();
        }
        return tissues;
    }

    public static Map<String, List<OncHistoryDetail>> getOncHistoryDetails(@NonNull String realm) {
        return getOncHistoryDetails(realm, null);
    }

    public static Map<String, List<OncHistoryDetail>> getOncHistoryDetailsByParticipantIds(@NonNull String realm, List<String> participantIds) {
        String queryAddition = " AND p.ddp_participant_id IN (?)".replace("?", DBUtil.participantIdsInClause(participantIds));
        return getOncHistoryDetails(realm, queryAddition);
    }

    public static Map<String, List<OncHistoryDetail>> getOncHistoryDetails(@NonNull String realm, String queryAddition) {
        logger.info("Collection oncHistoryDetail information");
        Map<String, List<OncHistoryDetail>> oncHistory = new HashMap<>();
        Map<Long, Tissue> tissues = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(DBUtil.getFinalQuery(SQL_SELECT_ONC_HISTORY_DETAIL, queryAddition) + SQL_ORDER_BY)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    Map<Long, OncHistoryDetail> oncHistoryMap = new HashMap<>();
                    while (rs.next()) {
                        long oncHistoryDetailId = rs.getLong(DBConstants.ONC_HISTORY_DETAIL_ID);
                        TissueSmId tissueSmId = Tissue.getSMIds(rs);
                        Tissue tissue;
                        if (tissueSmId != null && tissues.containsKey(tissueSmId.getTissueId())) {
                            tissue = tissues.get(tissueSmId.getTissueId());
                        } else {
                            tissue = Tissue.getTissue(rs);
                        }
                        if (tissueSmId != null) {
                            tissue.setSmIdBasedOnType(tissueSmId, rs);
                        }
                        if (tissue != null) {
                            tissues.put(tissue.getTissueId(), tissue);
                        }

                        if (!oncHistoryMap.containsKey(oncHistoryDetailId)) {
                            OncHistoryDetail oncHistoryDetail = getOncHistoryDetail(rs);
                            oncHistoryMap.put(oncHistoryDetailId, oncHistoryDetail);
                        }

                    }
                    //add tissues to their onc history
                    for (Tissue tissue : tissues.values()) {
                        long tissueOncHistoryDetailId = tissue.getOncHistoryDetailId();
                        OncHistoryDetail oncHistoryDetail = oncHistoryMap.get(tissueOncHistoryDetailId);
                        oncHistoryDetail.getTissues().add(tissue);
                    }//  add onchistories to their particiapnt
                    for (OncHistoryDetail oncHistoryDetail : oncHistoryMap.values()) {
                        //check if oncHistoryDetails is already in map
                        String ddpParticipantId = oncHistoryDetail.getDdpParticipantId();
                        List<OncHistoryDetail> oncHistoryDataList = oncHistory.getOrDefault(ddpParticipantId, new ArrayList<>());
                        oncHistoryDataList.add(oncHistoryDetail);
                        oncHistory.put(ddpParticipantId, oncHistoryDataList);
                    }

                } catch (Exception e) {
                    dbVals.resultException = e;
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't get list of oncHistories ", results.resultException);
        }

        logger.info("Got " + oncHistory.size() + " participants oncHistories in DSM DB for " + realm);
        return oncHistory;
    }

    public static OncHistoryDetail getOncHistoryDetail(@NonNull String oncHistoryDetailId, String realm) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ONC_HISTORY_DETAIL + QueryExtension.BY_ONC_HISTORY_DETAIL_ID)) {
                stmt.setString(1, realm);
                stmt.setString(2, oncHistoryDetailId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = getOncHistoryDetail(rs);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting oncHistoryDetails w/ id " + oncHistoryDetailId, results.resultException);
        }

        return (OncHistoryDetail) results.resultValue;
    }

    public static String createNewOncHistoryDetail(@NonNull String medicalRecordId, @NonNull String changedBy) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_ONC_HISTORY_DETAIL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, medicalRecordId);
                stmt.setString(2, OncHistoryDetail.STATUS_REVIEW);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setString(4, changedBy);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            String oncHistoryDetailId = rs.getString(1);
                            logger.info("Added new oncHistoryDetail for medicalRecord w/ id " + medicalRecordId);
                            dbVals.resultValue = oncHistoryDetailId;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error getting id of new institution ", e);
                    }
                } else {
                    throw new RuntimeException("Error adding new oncHistoryDetail for medicalRecord w/ id " + medicalRecordId + " it was updating " + result + " rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new oncHistoryDetail for medicalRecord w/ id " + medicalRecordId, results.resultException);
        } else {
            return (String) results.resultValue;
        }
    }

    public static Boolean hasReceivedDate(@NonNull Patch patch) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_TISSUE_RECEIVED)) {
                if (patch.getNameValue().getName().contains(DBConstants.DDP_TISSUE_ALIAS + DBConstants.ALIAS_DELIMITER)) {
                    stmt.setString(1, patch.getParentId());
                } else if (patch.getNameValue().getName().contains(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER)) {
                    stmt.setString(1, patch.getId());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String receivedDate = rs.getString(DBConstants.TISSUE_RECEIVED);
                        if (StringUtils.isNotBlank(receivedDate)) {
                            dbVals.resultValue = true;
                        } else {
                            dbVals.resultValue = false;
                        }
                    } else {
                        dbVals.resultException = new RuntimeException(" The patch id was not found in the table!");
                    }
                } catch (SQLException ex) {
                    dbVals.resultException = ex;
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException(" Error getting the received date of the OncHistory with Id:" + patch.getParentId(), results.resultException);
        }
        return (Boolean) results.resultValue;
    }
}
