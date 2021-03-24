package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.Patch;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.dsm.util.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
@TableName (
        name = DBConstants.DDP_ONC_HISTORY_DETAIL,
        alias = DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS,
        primaryKey = DBConstants.ONC_HISTORY_DETAIL_ID,
        columnPrefix = "")
public class OncHistoryDetail {

    private static final Logger logger = LoggerFactory.getLogger(OncHistoryDetail.class);

    public static final String SQL_SELECT_ONC_HISTORY_DETAIL = "SELECT p.ddp_participant_id, oD.onc_history_detail_id, oD.request, oD.deleted, oD.fax_sent, oD.tissue_received, oD.medical_record_id, oD.date_px, oD.type_px, " +
            "oD.location_px, oD.histology, oD.accession_number, oD.facility, oD.phone, oD.fax, oD.notes, oD.additional_values_json, " +
            "oD.request, oD.fax_sent, oD.fax_sent_by, oD.fax_confirmed, oD.fax_sent_2, oD.fax_sent_2_by, oD.fax_confirmed_2, oD.fax_sent_3, " +
            "oD.fax_sent_3_by, oD.fax_confirmed_3, oD.tissue_received, oD.tissue_problem_option, oD.gender, oD.destruction_policy, oD.unable_obtain_tissue, " +
            "tissue_id, t.notes, count_received, tissue_type, tissue_site, tumor_type, h_e, pathology_report, collaborator_sample_id, block_sent, scrolls_received, sk_id, sm_id, " +
            "sent_gp, first_sm_id, additional_tissue_value_json, expected_return, return_date, return_fedex_id, shl_work_number, tumor_percentage, tissue_sequence, " +
            " scrolls_count, uss_count, h_e_count, blocks_count " +
            "FROM ddp_onc_history_detail oD " +
            "LEFT JOIN ddp_medical_record m on (oD.medical_record_id = m.medical_record_id AND NOT oD.deleted <=> 1 AND NOT m.deleted <=> 1) " +
            "LEFT JOIN ddp_institution inst on (inst.institution_id = m.institution_id) " +
            "LEFT JOIN ddp_participant p on (p.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id AND NOT t.deleted <=> 1) " +
            "WHERE realm.instance_name = ? ";
    private static final String SQL_CREATE_ONC_HISTORY = "INSERT INTO ddp_onc_history_detail SET medical_record_id = ?, request = ?, last_changed = ?, changed_by = ?";
    private static final String SQL_SELECT_ONC_HISTORY = "SELECT onc_history_detail_id, medical_record_id, date_px, type_px, location_px, histology, accession_number, facility," +
            " phone, fax, notes, additional_values_json, request, fax_sent, fax_sent_by, fax_confirmed, fax_sent_2, fax_sent_2_by, fax_confirmed_2, fax_sent_3, fax_sent_3_by, fax_confirmed_3," +
            " tissue_received, gender, tissue_problem_option, destruction_policy FROM ddp_onc_history_detail WHERE NOT (deleted <=> 1)";
    private static final String SQL_SELECT_TISSUE_RECEIVED = "SELECT tissue_received FROM ddp_onc_history_detail WHERE onc_history_detail_id = ?";
    private static final String SQL_INSERT_ONC_HISTORY_DETAIL = "INSERT INTO ddp_onc_history_detail SET medical_record_id = ?, request = ?, last_changed = ?, changed_by = ?";
    public static final String SQL_ORDER_BY = " ORDER BY p.ddp_participant_id, inst.ddp_institution_id, oD.onc_history_detail_id ASC";
    public static final String SQL_SELECT_ONC_HISTORY_LAST_CHANGED = "SELECT oD.last_changed FROM ddp_institution inst " +
            "LEFT JOIN ddp_participant as p on (p.participant_id = inst.participant_id) LEFT JOIN ddp_instance as ddp on (ddp.ddp_instance_id = p.ddp_instance_id) " +
            "LEFT JOIN ddp_medical_record as m on (m.institution_id = inst.institution_id AND NOT m.deleted <=> 1) LEFT JOIN ddp_onc_history_detail as oD on (m.medical_record_id = oD.medical_record_id) " +
            "WHERE p.participant_id = ?";

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

    private String oncHistoryDetailId;
    private final String medicalRecordId;

    @ColumnName (DBConstants.DATE_PX)
    private String datePX;

    @ColumnName (DBConstants.TYPE_PX)
    private String typePX;

    @ColumnName (DBConstants.LOCATION_PX)
    private String locationPX;

    @ColumnName (DBConstants.HISTOLOGY)
    private String histology;

    @ColumnName (DBConstants.ACCESSION_NUMBER)
    private String accessionNumber;

    @ColumnName (DBConstants.FACILITY)
    private String facility;

    @ColumnName (DBConstants.PHONE)
    private String fPhone;

    @ColumnName (DBConstants.FAX)
    private String fFax;

    @ColumnName (DBConstants.NOTES)
    private String oncHisNotes;

    @ColumnName (DBConstants.REQUEST)
    private String request;

    @ColumnName (DBConstants.FAX_SENT)
    private String tFaxSent;

    @ColumnName (DBConstants.FAX_SENT_BY)
    private String tFaxSentBy;

    @ColumnName (DBConstants.FAX_CONFIRMED)
    private String tFaxConfirmed;

    @ColumnName (DBConstants.FAX_SENT_2)
    private String tFaxSent2;

    @ColumnName (DBConstants.FAX_SENT_2_BY)
    private String tFaxSent2By;

    @ColumnName (DBConstants.FAX_CONFIRMED_2)
    private String tFaxConfirmed2;

    @ColumnName (DBConstants.FAX_SENT_3)
    private String tFaxSent3;

    @ColumnName (DBConstants.FAX_SENT_3_BY)
    private String tFaxSent3By;

    @ColumnName (DBConstants.FAX_CONFIRMED_3)
    private String tFaxConfirmed3;

    @ColumnName (DBConstants.TISSUE_RECEIVED)
    private String tissueReceived;

    @ColumnName (DBConstants.TISSUE_PROBLEM_OPTION)
    private String tissueProblemOption;

    @ColumnName (DBConstants.GENDER)
    private String gender;

    @ColumnName (DBConstants.ADDITIONAL_VALUES)
    private String additionalValues;

    @ColumnName (DBConstants.DESTRUCTION_POLICY)
    private String destructionPolicy;

    private String changedBy;

    @ColumnName (DBConstants.DELETED)
    private boolean deleted;

    @ColumnName (DBConstants.UNABLE_OBTAIN_TISSUE)
    private boolean unableToObtain;

    private String participantId;


    private List<Tissue> tissues;

    public OncHistoryDetail(String oncHistoryDetailId, String medicalRecordId, String datePX, String typePX,
                            String locationPX, String histology, String accessionNumber, String facility, String fPhone,
                            String fFax, String oncHisNotes, String request, String tFaxSent,
                            String tFaxSentBy, String tFaxConfirmed,
                            String tFaxSent2, String tFaxSent2By, String tFaxConfirmed2,
                            String tFaxSent3, String tFaxSent3By, String tFaxConfirmed3,
                            String tissueReceived, String gender, String additionalValues,
                            String tissueProblemOption, String destructionPolicy, boolean unableToObtain) {
        this.oncHistoryDetailId = oncHistoryDetailId;
        this.medicalRecordId = medicalRecordId;
        this.datePX = datePX;
        this.typePX = typePX;
        this.locationPX = locationPX;
        this.histology = histology;
        this.accessionNumber = accessionNumber;
        this.facility = facility;
        this.fPhone = fPhone;
        this.fFax = fFax;
        this.oncHisNotes = oncHisNotes;
        this.request = request;
        this.tFaxSent = tFaxSent;
        this.tFaxSentBy = tFaxSentBy;
        this.tFaxConfirmed = tFaxConfirmed;
        this.tFaxSent2 = tFaxSent2;
        this.tFaxSent2By = tFaxSent2By;
        this.tFaxConfirmed2 = tFaxConfirmed2;
        this.tFaxSent3 = tFaxSent3;
        this.tFaxSent3By = tFaxSent3By;
        this.tFaxConfirmed3 = tFaxConfirmed3;
        this.tissueReceived = tissueReceived;
        this.gender = gender;
        this.additionalValues = additionalValues;
        this.tissues = new ArrayList<>();
        this.tissueProblemOption = tissueProblemOption;
        this.destructionPolicy = destructionPolicy;
        this.unableToObtain = unableToObtain;
    }

    public OncHistoryDetail(String oncHistoryDetailId, String medicalRecordId, String datePX, String typePX,
                            String locationPX, String histology, String accessionNumber, String facility, String fPhone,
                            String fFax, String oncHisNotes, String request, String tFaxSent,
                            String tFaxSentBy, String tFaxConfirmed,
                            String tFaxSent2, String tFaxSent2By, String tFaxConfirmed2,
                            String tFaxSent3, String tFaxSent3By, String tFaxConfirmed3,
                            String tissueReceived, String gender, String additionalValues, List<Tissue> tissues,
                            String tissueProblemOption, String destructionPolicy, boolean unableToObtain) {
        this.oncHistoryDetailId = oncHistoryDetailId;
        this.medicalRecordId = medicalRecordId;
        this.datePX = datePX;
        this.typePX = typePX;
        this.locationPX = locationPX;
        this.histology = histology;
        this.accessionNumber = accessionNumber;
        this.facility = facility;
        this.fPhone = fPhone;
        this.fFax = fFax;
        this.oncHisNotes = oncHisNotes;
        this.request = request;
        this.tFaxSent = tFaxSent;
        this.tFaxSentBy = tFaxSentBy;
        this.tFaxConfirmed = tFaxConfirmed;
        this.tFaxSent2 = tFaxSent2;
        this.tFaxSent2By = tFaxSent2By;
        this.tFaxConfirmed2 = tFaxConfirmed2;
        this.tFaxSent3 = tFaxSent3;
        this.tFaxSent3By = tFaxSent3By;
        this.tFaxConfirmed3 = tFaxConfirmed3;
        this.tissueReceived = tissueReceived;
        this.gender = gender;
        this.additionalValues = additionalValues;
        this.tissues = tissues;
        this.tissueProblemOption = tissueProblemOption;
        this.destructionPolicy = destructionPolicy;
        this.unableToObtain = unableToObtain;
    }

    public static OncHistoryDetail getOncHistoryDetail(@NonNull ResultSet rs) throws SQLException {
        List tissues = new ArrayList<>();
        OncHistoryDetail oncHistoryDetail = new OncHistoryDetail(
                rs.getString(DBConstants.ONC_HISTORY_DETAIL_ID),
                rs.getString(DBConstants.MEDICAL_RECORD_ID),
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
                rs.getString(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.ADDITIONAL_VALUES), tissues,
                rs.getString(DBConstants.TISSUE_PROBLEM_OPTION),
                rs.getString(DBConstants.DESTRUCTION_POLICY),
                rs.getBoolean(DBConstants.UNABLE_OBTAIN_TISSUE)
        );

        return oncHistoryDetail;
    }

    public void addTissue(Tissue tissue) {
        if (tissues != null) {
            tissues.add(tissue);
        }
    }

    public static Map<String, List<OncHistoryDetail>> getOncHistoryDetails(@NonNull String realm) {
        return getOncHistoryDetails(realm, null);
    }

    public static Map<String, List<OncHistoryDetail>> getOncHistoryDetails(@NonNull String realm, String queryAddition) {
        logger.info("Collection oncHistoryDetail information");
        Map<String, List<OncHistoryDetail>> oncHistory = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(DBUtil.getFinalQuery(SQL_SELECT_ONC_HISTORY_DETAIL, queryAddition) + SQL_ORDER_BY)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    Map<String, OncHistoryDetail> oncHistoryMap = new HashMap<>();
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        String oncHistoryDetailId = rs.getString(DBConstants.ONC_HISTORY_DETAIL_ID);

                        Tissue tissue = Tissue.getTissue(rs);

                        //check if oncHistoryDetails is already in map
                        List<OncHistoryDetail> oncHistoryDataList = new ArrayList<>();
                        if (oncHistory.containsKey(ddpParticipantId)) {
                            oncHistoryDataList = oncHistory.get(ddpParticipantId);
                        }
                        else {
                            oncHistory.put(ddpParticipantId, oncHistoryDataList);
                            oncHistoryMap = new HashMap<>();
                        }

                        OncHistoryDetail oncHistoryDetail = null;
                        if (oncHistoryMap.containsKey(oncHistoryDetailId)) {
                            oncHistoryDetail = oncHistoryMap.get(oncHistoryDetailId);
                            oncHistoryDetail.addTissue(tissue);
                        }
                        else {
                            oncHistoryDetail = getOncHistoryDetail(rs);
                            oncHistoryDetail.addTissue(tissue);
                            oncHistoryDataList.add(oncHistoryDetail);
                        }
                        oncHistoryMap.put(oncHistoryDetailId, oncHistoryDetail);
                    }
                }
            }
            catch (SQLException ex) {
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
            }
            catch (SQLException ex) {
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
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Error getting id of new institution ", e);
                    }
                }
                else {
                    throw new RuntimeException("Error adding new oncHistoryDetail for medicalRecord w/ id " + medicalRecordId + " it was updating " + result + " rows");
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new oncHistoryDetail for medicalRecord w/ id " + medicalRecordId, results.resultException);
        }
        else {
            return (String) results.resultValue;
        }
    }

    public static Boolean hasReceivedDate(@NonNull Patch patch) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_TISSUE_RECEIVED)) {
                if (patch.getNameValue().getName().contains(DBConstants.DDP_TISSUE_ALIAS + DBConstants.ALIAS_DELIMITER)) {
                    stmt.setString(1, patch.getParentId());
                }
                else if (patch.getNameValue().getName().contains(DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS + DBConstants.ALIAS_DELIMITER)) {
                    stmt.setString(1, patch.getId());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String receivedDate = rs.getString(DBConstants.TISSUE_RECEIVED);
                        if (StringUtils.isNotBlank(receivedDate)) {
                            dbVals.resultValue = true;
                        }
                        else {
                            dbVals.resultValue = false;
                        }
                    }
                    else {
                        dbVals.resultException = new RuntimeException(" The patch id was not found in the table!");
                    }
                }
                catch (SQLException ex) {
                    dbVals.resultException = ex;
                }
            }
            catch (SQLException ex) {
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
