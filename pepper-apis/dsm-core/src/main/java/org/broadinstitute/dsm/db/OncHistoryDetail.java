package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;
import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDto;
import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainless;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.db.SimpleResult;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@TableName(name = DBConstants.DDP_ONC_HISTORY_DETAIL, alias = DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS,
        primaryKey = DBConstants.ONC_HISTORY_DETAIL_ID, columnPrefix = "")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OncHistoryDetail implements HasDdpInstanceId {

    public static final String SQL_SELECT_ONC_HISTORY_DETAIL =
            "SELECT p.ddp_participant_id, p.ddp_instance_id, p.participant_id, oD.onc_history_detail_id, oD.request, "
                    + "oD.fax_sent, oD.tissue_received, oD.medical_record_id, oD.date_px, oD.type_px, "
                    + "oD.location_px, oD.histology, oD.accession_number, oD.facility, oD.phone, oD.fax, oD.notes, "
                    + "oD.additional_values_json, oD.request, oD.fax_sent, oD.fax_sent_by, oD.fax_confirmed, oD.fax_sent_2, "
                    + "oD.fax_sent_2_by, oD.fax_confirmed_2, oD.fax_sent_3, "
                    + "oD.fax_sent_3_by, oD.fax_confirmed_3, oD.tissue_received, oD.tissue_problem_option, oD.gender, "
                    + "oD.destruction_policy, oD.unable_obtain_tissue, "
                    + "t.tissue_id, t.notes, count_received, tissue_type, tissue_site, tumor_type, h_e, pathology_report, "
                    + "collaborator_sample_id, block_sent, scrolls_received, sk_id, sm_id, "
                    + "sent_gp, first_sm_id, additional_tissue_value_json, expected_return, return_date, return_fedex_id, "
                    + "shl_work_number, block_id_shl, tumor_percentage, tissue_sequence, "
                    + "scrolls_count, uss_count, h_e_count, blocks_count, sm.sm_id_value, sm.sm_id_type_id, sm.sm_id_pk, "
                    + "sm.tissue_id, smt.sm_id_type FROM ddp_onc_history_detail oD "
                    + "LEFT JOIN ddp_medical_record m on (oD.medical_record_id = m.medical_record_id AND NOT m.deleted <=> 1) "
                    + "LEFT JOIN ddp_institution inst on (inst.institution_id = m.institution_id) "
                    + "LEFT JOIN ddp_participant p on (p.participant_id = inst.participant_id) "
                    + "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) "
                    + "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id) "
                    + "LEFT JOIN sm_id sm on (sm.tissue_id = t.tissue_id) "
                    + "LEFT JOIN sm_id_type smt on (smt.sm_id_type_id = sm.sm_id_type_id ) WHERE realm.instance_name = ? ";
    public static final String SQL_ORDER_BY =
            " ORDER BY p.ddp_participant_id, inst.ddp_institution_id, oD.onc_history_detail_id, t.tissue_id ASC";
    public static final String SQL_SELECT_ONC_HISTORY_LAST_CHANGED = "SELECT oD.last_changed FROM ddp_institution inst "
            + "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) "
            + "LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) "
            + "LEFT JOIN ddp_medical_record as m on (m.institution_id = inst.institution_id) "
            + "LEFT JOIN ddp_onc_history_detail as oD on (m.medical_record_id = oD.medical_record_id) " + "WHERE p.participant_id = ?";

    public static final String SQL_SELECT_ONC_HISTORY_DETAIL_BY_MEDICAL_RECORD =
            "SELECT onc.* FROM ddp_onc_history_detail onc WHERE medical_record_id = ?";

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
    public static final String ONC_HISTORY_DETAIL_ID = "oncHistoryDetailId";
    private static final Logger logger = LoggerFactory.getLogger(OncHistoryDetail.class);
    private static final String SQL_SELECT_ONC_HISTORY =
            "SELECT onc_history_detail_id, medical_record_id, date_px, type_px, location_px, histology, accession_number, facility,"
                    + " phone, fax, notes, additional_values_json, request, fax_sent, fax_sent_by, fax_confirmed, fax_sent_2, "
                    + "fax_sent_2_by, fax_confirmed_2, fax_sent_3, fax_sent_3_by, fax_confirmed_3,"
                    + " tissue_received, gender, tissue_problem_option, destruction_policy FROM ddp_onc_history_detail ";
    private static final String SQL_INSERT_ONC_HISTORY_DETAIL =
            "INSERT INTO ddp_onc_history_detail SET medical_record_id = ?, request = ?, last_changed = ?, changed_by = ?";

    private static final String SQL_CREATE_ONC_HISTORY_DETAIL =
            "INSERT INTO ddp_onc_history_detail SET medical_record_id = ?, date_px = ?, type_px = ?, location_px = ?, facility = ?, "
                    + "request = ?, destruction_policy = ?, last_changed = ?, changed_by = ?";

    private static final String SQL_DELETE_ONC_HISTORY_DETAIL =
            "delete from ddp_onc_history_detail where onc_history_detail_id = ?";

    private static final String SQL_UPDATE_DESTRUCTION_POLICY =
            "UPDATE ddp_onc_history_detail onc "
                    + "LEFT JOIN ddp_medical_record med ON med.medical_record_id = onc.medical_record_id "
                    + "LEFT JOIN ddp_institution di ON di.institution_id = med.institution_id "
                    + "LEFT JOIN ddp_participant dp ON dp.participant_id = di.participant_id "
                    + "SET onc.destruction_policy = ?, onc.last_changed = ?, onc.changed_by = ? "
                    + "WHERE onc.facility = ? AND dp.ddp_instance_id = ?";

    @ColumnName(DBConstants.ONC_HISTORY_DETAIL_ID)
    private int oncHistoryDetailId;

    @ColumnName(DBConstants.MEDICAL_RECORD_ID)
    private int medicalRecordId;

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
    @ColumnName(DBConstants.DESTRUCTION_POLICY)
    private String destructionPolicy;
    private String changedBy;
    @ColumnName(DBConstants.UNABLE_OBTAIN_TISSUE)
    private boolean unableObtainTissue;
    private String participantId;
    private String ddpParticipantId;
    private List<Tissue> tissues;
    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    private Long ddpInstanceId;

    public OncHistoryDetail() {
    }

    public OncHistoryDetail(long ddpInstanceId) {
        this.ddpInstanceId = ddpInstanceId;
    }

    public OncHistoryDetail(Integer oncHistoryDetailId, Integer medicalRecordId, String datePx, String typePx, String locationPx,
                            String histology, String accessionNumber, String facility, String phone, String fax, String notes,
                            String request, String faxSent, String faxSentBy, String faxConfirmed, String faxSent2, String faxSent2By,
                            String faxConfirmed2, String faxSent3, String faxSent3By, String faxConfirmed3, String tissueReceived,
                            String gender, String additionalValuesJson, String tissueProblemOption, String destructionPolicy,
                            boolean unableObtainTissue) {
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

    public OncHistoryDetail(Integer oncHistoryDetailId, Integer medicalRecordId, String datePx, String typePx, String locationPx,
                            String histology, String accessionNumber, String facility, String phone, String fax, String notes,
                            String request, String faxSent, String faxSentBy, String faxConfirmed, String faxSent2, String faxSent2By,
                            String faxConfirmed2, String faxSent3, String faxSent3By, String faxConfirmed3, String tissueReceived,
                            String gender, String additionalValuesJson, List<Tissue> tissues, String tissueProblemOption,
                            String destructionPolicy, boolean unableObtainTissue, String participantId, String ddpParticipantId,
                            long ddpInstanceId) {
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
        this.ddpInstanceId = ddpInstanceId;
    }

    public OncHistoryDetail(Builder builder) {
        this.medicalRecordId = builder.medicalRecordId;
        this.datePx = builder.datePx;
        this.typePx = builder.typePx;
        this.locationPx = builder.locationPx;
        this.facility = builder.facility;
        this.request = builder.request;
        this.destructionPolicy = builder.destructionPolicy;
        this.changedBy = builder.changedBy;
        this.ddpInstanceId = (long)builder.ddpInstanceId;
    }

    public static OncHistoryDetail getOncHistoryDetail(@NonNull ResultSet rs) throws SQLException {
        List tissues = new ArrayList<>();
        OncHistoryDetail oncHistoryDetail =
                new OncHistoryDetail(rs.getInt(DBConstants.ONC_HISTORY_DETAIL_ID), rs.getInt(DBConstants.MEDICAL_RECORD_ID),
                        rs.getString(DBConstants.DATE_PX), rs.getString(DBConstants.TYPE_PX), rs.getString(DBConstants.LOCATION_PX),
                        rs.getString(DBConstants.HISTOLOGY), rs.getString(DBConstants.ACCESSION_NUMBER), rs.getString(DBConstants.FACILITY),
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
                        rs.getString(DBConstants.TISSUE_RECEIVED), rs.getString(DBConstants.GENDER), rs.getString(
                        DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.ADDITIONAL_VALUES_JSON),
                        tissues, rs.getString(DBConstants.TISSUE_PROBLEM_OPTION), rs.getString(DBConstants.DESTRUCTION_POLICY),
                        rs.getBoolean(DBConstants.UNABLE_OBTAIN_TISSUE), rs.getString(DBConstants.PARTICIPANT_ID),
                        rs.getString(DBConstants.DDP_PARTICIPANT_ID), rs.getInt(DBConstants.DDP_INSTANCE_ID));
        return oncHistoryDetail;
    }

    // TODO: there should be no need for the realm parameter -DC
    public static OncHistoryDetail getOncHistoryDetail(@NonNull int oncHistoryDetailId, String realm) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ONC_HISTORY_DETAIL + QueryExtension.BY_ONC_HISTORY_DETAIL_ID)) {
                stmt.setString(1, realm);
                stmt.setInt(2, oncHistoryDetailId);
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

    public static Map<String, List<OncHistoryDetail>> getOncHistoryDetails(@NonNull String realm) {
        return getOncHistoryDetails(realm, null);
    }

    public static Map<String, List<OncHistoryDetail>> getOncHistoryDetails(@NonNull String realm, String queryAddition) {
        logger.info("Collection oncHistoryDetail information");
        Map<String, List<OncHistoryDetail>> oncHistory = new HashMap<>();
        Map<Integer, Tissue> tissues = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    DBUtil.getFinalQuery(SQL_SELECT_ONC_HISTORY_DETAIL, queryAddition) + SQL_ORDER_BY)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    Map<Integer, OncHistoryDetail> oncHistoryMap = new HashMap<>();
                    while (rs.next()) {
                        int oncHistoryDetailId = rs.getInt(DBConstants.ONC_HISTORY_DETAIL_ID);
                        SmId tissueSmId = Tissue.getSMIds(rs);
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
                        int tissueOncHistoryDetailId = tissue.getOncHistoryDetailId();
                        OncHistoryDetail oncHistoryDetail = oncHistoryMap.get(tissueOncHistoryDetailId);
                        oncHistoryDetail.getTissues().add(tissue);
                    } //  add onchistories to their particiapnt
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
            throw new DsmInternalError(
                    String.format("Couldn't get list of oncHistories with queryAddition '%s' and for realm %s",
                            queryAddition, realm),
                    results.resultException);
        }

        logger.info("Got " + oncHistory.size() + " participants oncHistories in DSM DB for " + realm);
        return oncHistory;
    }

    public static Map<String, List<OncHistoryDetail>> getOncHistoryDetailsByParticipantIds(@NonNull String realm,
                                                                                           List<String> participantIds) {
        logger.info("Getting onc histories for participants {}", String.join(", ", participantIds));
        String queryAddition = " AND p.ddp_participant_id IN (?)".replace("?", DBUtil.participantIdsInClause(participantIds));
        return getOncHistoryDetails(realm, queryAddition);
    }

    public static List<OncHistoryDetailDto> getOncHistoryDetailByMedicalRecord(int medicalRecordId) {
        return inTransaction(conn -> {
            List<OncHistoryDetailDto> records = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ONC_HISTORY_DETAIL_BY_MEDICAL_RECORD)) {
                stmt.setInt(1, medicalRecordId);
                try (ResultSet rs = stmt.executeQuery()) {
                    OncHistoryDetailDaoImpl.BuildOncHistoryDetailDto builder = new OncHistoryDetailDaoImpl.BuildOncHistoryDetailDto();
                    while (rs.next()) {
                        records.add(builder.build(rs));
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting onc history detail for medical record ID=" + medicalRecordId, e);
            }
            return records;
        });
    }

    public static int createOncHistoryDetail(@NonNull int medicalRecordId, @NonNull String changedBy) {
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_ONC_HISTORY_DETAIL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, medicalRecordId);
                stmt.setString(2, OncHistoryDetail.STATUS_REVIEW);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setString(4, changedBy);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int id = rs.getInt(1);
                            logger.info("Added new oncHistoryDetail (ID={}) for medicalRecord {}", id, medicalRecordId);
                            return id;
                        }
                    }
                }
                throw new DsmInternalError(String.format("Error creating ddp_onc_history_detail for medical record %d: "
                        + "result key not present", medicalRecordId));
            } catch (SQLException ex) {
                throw new DsmInternalError("Error creating ddp_onc_history_detail for medical record " + medicalRecordId);
            }
        });
    }

    /**
     * Note: this method does not create all OncHistoryDetail fields. Add those fields as needed.
     */
    public static int createOncHistoryDetail(OncHistoryDetail oncHistoryDetail) {
        int medicalRecordId = oncHistoryDetail.getMedicalRecordId();
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_CREATE_ONC_HISTORY_DETAIL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, medicalRecordId);
                stmt.setString(2, oncHistoryDetail.datePx);
                stmt.setString(3, oncHistoryDetail.typePx);
                stmt.setString(4, oncHistoryDetail.locationPx);
                stmt.setString(5, oncHistoryDetail.facility);
                stmt.setString(6, oncHistoryDetail.request);
                stmt.setString(7, oncHistoryDetail.destructionPolicy);
                stmt.setLong(8, System.currentTimeMillis());
                stmt.setString(9, oncHistoryDetail.changedBy);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
                throw new DsmInternalError(String.format("Error creating ddp_onc_history_detail for medical record %d: "
                        + "result key not present", medicalRecordId));
            } catch (SQLException ex) {
                throw new DsmInternalError("Error creating ddp_onc_history_detail for medical record " + medicalRecordId);
            }
        });
    }

    public static void delete(int oncHistoryDetailId) {
        DaoUtil.deleteSingleRowById(oncHistoryDetailId, SQL_DELETE_ONC_HISTORY_DETAIL);
    }

    @JsonProperty("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        return ObjectMapperSingleton.readValue(additionalValuesJson, new TypeReference<Map<String, Object>>() {
        });
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

    @Override
    public Optional<Long> extractDdpInstanceId() {
        return Optional.ofNullable(getDdpInstanceId());
    }

    /**
     * Verify that a participant is associated with an institution of type NOT_SPECIFIED, and create that
     * institution and related medical record if the verification fails.
     *
     * @param updateElastic true if ElasticSearch index should be updated with new institution data
     *
     * @return medical record ID associated with the institution
     */
    public static int verifyOrCreateMedicalRecord(int participantId, String ddpParticipantId, String realm,
                                                  boolean updateElastic) {
        Number mrId = MedicalRecordUtil.isInstitutionTypeInDB(Integer.toString(participantId));
        if (mrId == null) {
            MedicalRecordUtil.writeInstitutionIntoDb(participantId, ddpParticipantId, MedicalRecordUtil.NOT_SPECIFIED, realm,
                    updateElastic);
            Integer id = MedicalRecordUtil.getParticipantIdByDdpParticipantId(ddpParticipantId, realm);
            if (id == null) {
                throw new DsmInternalError("Error adding new institution for oncHistory. Participant ID: " + participantId);
            }
            mrId = MedicalRecordUtil.isInstitutionTypeInDB(Integer.toString(participantId));
            if (mrId == null) {
                throw new DsmInternalError("Medical record ID not found for new record");
            }
        }
        return mrId.intValue();
    }

    public static void updateDestructionPolicy(@NonNull String policy, @NonNull String facility, @NonNull String realm,
                                               @NonNull String user) {
        DDPInstanceDto ddpInstance = DDPInstanceDao.of().getDDPInstanceByInstanceName(realm).orElseThrow(() ->
                new DSMBadRequestException("Invalid realm : " + realm));

        int updateCnt = inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_DESTRUCTION_POLICY)) {
                stmt.setString(1, policy);
                stmt.setString(2, String.valueOf(System.currentTimeMillis()));
                stmt.setString(3, user);
                stmt.setString(4, facility);
                stmt.setInt(5, ddpInstance.getDdpInstanceId());
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new DsmInternalError("Error updating destruction policy for facility " + facility, e);
            }
        });

        if (updateCnt > 0) {
            String index = ddpInstance.getEsParticipantIndex();

            String scriptText = String.format("if (ctx._source.dsm.oncHistoryDetail != null) "
                    + "{for (a in ctx._source.dsm.oncHistoryDetail) "
                    + "{if (a.facility == '%s') {a.destructionPolicy = '%s';}}}", facility, policy);
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
            int instanceId = ddpInstance.getDdpInstanceId();
            MatchQueryBuilder qb = new MatchQueryBuilder("dsm.medicalRecord.ddpInstanceId", instanceId);
            queryBuilder.must(QueryBuilders.nestedQuery("dsm.medicalRecord", qb, ScoreMode.None));

            try {
                UpsertPainless upsert = new UpsertPainless(null, index, null, queryBuilder);
                upsert.export(scriptText, Collections.emptyMap(), "destructionPolicy");
            } catch (Exception e) {
                String msg = String.format("Error updating ElasticSearch oncHistoryDetail destruction policy for index %s, "
                        + "facility=%s, policy=%s", index, facility, policy);
                throw new DsmInternalError(msg, e);
            }
        }
    }

    // Note: this builder is not complete, add fields as needed
    public static class Builder {
        private int medicalRecordId;
        private String datePx;
        private String typePx;
        private String locationPx;
        private String facility;
        private String request;
        private String destructionPolicy;
        private String changedBy;
        private int ddpInstanceId;

        public Builder withMedicalRecordId(int medicalRecordId) {
            this.medicalRecordId = medicalRecordId;
            return this;
        }

        public Builder withDatePx(String datePx) {
            this.datePx = datePx;
            return this;
        }

        public Builder withTypePx(String typePx) {
            this.typePx = typePx;
            return this;
        }

        public Builder withLocationPx(String locationPx) {
            this.locationPx = locationPx;
            return this;
        }

        public Builder withFacility(String facility) {
            this.facility = facility;
            return this;
        }

        public Builder withRequest(String request) {
            this.request = request;
            return this;
        }

        public Builder withDestructionPolicy(String destructionPolicy) {
            this.destructionPolicy = destructionPolicy;
            return this;
        }

        public Builder withChangedBy(String changedBy) {
            this.changedBy = changedBy;
            return this;
        }

        public Builder withDdpInstanceId(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
            return this;
        }

        public OncHistoryDetail build() {
            return new OncHistoryDetail(this);
        }
    }
}
