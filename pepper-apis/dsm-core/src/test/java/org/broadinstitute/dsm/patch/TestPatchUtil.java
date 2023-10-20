package org.broadinstitute.dsm.patch;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;
import static org.broadinstitute.dsm.service.admin.UserAdminService.USER_ADMIN_ROLE;
import static org.broadinstitute.dsm.statics.DBConstants.MR_VIEW;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.ddp.tissue.TissueSMIDDao;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.service.admin.UserAdminTestUtil;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class TestPatchUtil {
    private static final UserAdminTestUtil cmiAdminUtil = new UserAdminTestUtil();
    private static final String INSERT_PARTICIPANT =
            "INSERT INTO ddp_participant (ddp_participant_id, ddp_instance_id, last_version_date, last_changed) VALUES (?, ?, ?,?) ";
    private static final String INSERT_INSTITUTION =
            "INSERT INTO ddp_institution (participant_id, ddp_institution_id, type, last_changed) VALUES (?, ?, 'NOT_SPECIFIED',?) ";
    private static final String INSERT_MEDICAL_RECORD =
            "INSERT INTO ddp_medical_record (institution_id, last_changed, changed_by) VALUES (?,?, 'TEST') ";
    private static final String INSERT_DDP_ONC_HISTORY_DETAIL =
            "INSERT INTO ddp_onc_history_detail (medical_record_id, last_changed, changed_by) VALUES (?,?, 'TEST') ";
    private static final String INSERT_GET_ONC_HISTORY_BY_ID =
            "SELECT p.ddp_participant_id, p.ddp_instance_id, p.participant_id, oD.onc_history_detail_id, oD.request, oD.deleted, "
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
                    + "scrolls_count, uss_count, h_e_count, blocks_count, sm.sm_id_value, sm.sm_id_type_id, sm.sm_id_pk, sm.deleted, "
                    + "sm.tissue_id, smt.sm_id_type FROM ddp_onc_history_detail oD "
                    + "LEFT JOIN ddp_medical_record m on (oD.medical_record_id = m.medical_record_id) "
                    + "LEFT JOIN ddp_institution inst on (inst.institution_id = m.institution_id) "
                    + "LEFT JOIN ddp_participant p on (p.participant_id = inst.participant_id) "
                    + "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) "
                    + "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id ) "
                    + "LEFT JOIN sm_id sm on (sm.tissue_id = t.tissue_id  ) "
                    + "LEFT JOIN sm_id_type smt on (smt.sm_id_type_id = sm.sm_id_type_id ) WHERE oD.onc_history_detail_id = ? ";

    private static final String SQL_SELECT_TISSUE =
            "SELECT t.tissue_id, onc_history_detail_id, notes, count_received, tissue_type, tissue_site, tumor_type, h_e, "
                    + "pathology_report, collaborator_sample_id, block_sent, expected_return, return_date, return_fedex_id, "
                    + "scrolls_received, sk_id, sm_id, t.deleted, sm.deleted,  smt.sm_id_type, "
                    + "scrolls_count, uss_count, blocks_count, h_e_count, first_sm_id, sent_gp, t.last_changed, t.changed_by, "
                    + "additional_tissue_value_json, shl_work_number, block_id_shl, "
                    + "tumor_percentage, tissue_sequence, sm.sm_id_value, sm.sm_id_type_id, sm.sm_id_pk, sm.deleted, sm.tissue_id "
                    + "FROM ddp_tissue t "
                    + "LEFT JOIN sm_id sm on (sm.tissue_id = t.tissue_id ) "
                    + "LEFT JOIN sm_id_type smt on (smt.sm_id_type_id = sm.sm_id_type_id ) "
                    + "WHERE onc_history_detail_id = ?";
    private static final String INSERT_TISSUE =
            "INSERT INTO ddp_tissue (onc_history_detail_id, last_changed, changed_by) VALUES (?,?, 'TEST') ";
    private static final String INSERT_USS_SM_ID = "INSERT INTO sm_id (tissue_id, sm_id_value, sm_id_type_id, last_changed, changed_by) "
            + "VALUES (?, ?, ?, ?, 'TEST') ";
    public static String ddpGroupId;
    public static String ddpInstanceId;
    public static String userEmail;
    private static String instanceName;
    private static String groupName;
    private static String studyGuid;
    private static String collaboratorPrefix;

    public TestPatchUtil(String instanceName, String studyGuid, String collaboratorPrefix, String groupName) {
        this.instanceName = instanceName;
        this.studyGuid = studyGuid;
        this.collaboratorPrefix = collaboratorPrefix;
        this.groupName = groupName;
        this.userEmail = generateUserEmail();
        setupStudy();
    }

    private static String generateUserEmail() {
        return "Test-" + System.currentTimeMillis() + "@broad.dev";
    }

    public static String getPrimaryKey(ResultSet rs, String table) throws SQLException {
        if (rs.next()) {
            return rs.getString(1);
        } else {
            throw new DsmInternalError(String.format("Unable to set up data in %s for juniper, going to role back transaction", table));
        }
    }

    private static void delete(String tableName, String primaryColumn, String id) {
        SimpleResult results = inTransaction((conn) -> {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE from " + tableName + " WHERE " + primaryColumn + " = ? ;")) {
                stmt.setString(1, id);
                try {
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    throw new DsmInternalError("Error deleting from " + tableName, e);
                }
            } catch (SQLException ex) {
                throw new DsmInternalError("Error deleting ", ex);
            }
            return null;
        });
    }

    public static List<Tissue> getTissue(@NonNull String oncHistoryDetailId) {
        List<Tissue> tissueList = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            Map<Integer, Tissue> tissues = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_TISSUE)) {
                stmt.setString(1, oncHistoryDetailId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
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
                        tissues.put(tissue.getTissueId(), tissue);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }

            if (dbVals.resultException != null) {
                throw new RuntimeException("Error getting tissue for oncHistoryDetails w/ id " + oncHistoryDetailId,
                        dbVals.resultException);
            }
            dbVals.resultValue = tissues.values();
            return dbVals;
            }
        );
        tissueList.addAll((Collection<? extends Tissue>) results.resultValue);
        return tissueList;
    }

    public void setupStudy() {
        //everything should get inserted in one transaction
        cmiAdminUtil.createRealmAndStudyGroup(instanceName, studyGuid, collaboratorPrefix, groupName);
        ddpInstanceId = String.valueOf(cmiAdminUtil.getDdpInstanceId());
        ddpGroupId = String.valueOf(cmiAdminUtil.getStudyGroupId());
        cmiAdminUtil.setStudyAdminAndRoles(userEmail, USER_ADMIN_ROLE,
                Arrays.asList(MR_VIEW));
    }

    public String createParticipantForStudy(String guid) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            PreparedStatement stmt = null;
            try {
                stmt = conn.prepareStatement(INSERT_PARTICIPANT, Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, guid);
                stmt.setString(2, ddpInstanceId);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.setLong(4, System.currentTimeMillis());
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                dbVals.resultValue = getPrimaryKey(rs, "participant_id");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return dbVals;
        });

        return (String) results.resultValue;
    }

    public String createOncHistoryForParticipant(String medicalRecordId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            PreparedStatement stmt = null;
            try {
                stmt = conn.prepareStatement(INSERT_DDP_ONC_HISTORY_DETAIL, Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, medicalRecordId);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                dbVals.resultValue = getPrimaryKey(rs, "medical_record_id");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return dbVals;
        });

        return (String) results.resultValue;

    }

    public OncHistoryDetail getOncHistoryDetailById(String oncHistoryDetailId) {
        List<Tissue> tissues = getTissue(oncHistoryDetailId);
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            PreparedStatement stmt = null;
            try {
                stmt = conn.prepareStatement(INSERT_GET_ONC_HISTORY_BY_ID);
                stmt.setString(1, oncHistoryDetailId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    OncHistoryDetail oncHistoryDetail = OncHistoryDetail.getOncHistoryDetail(rs);
                    oncHistoryDetail.setTissues(tissues);
                    dbVals.resultValue = oncHistoryDetail;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return dbVals;
        });

        return (OncHistoryDetail) results.resultValue;

    }

    public String createTissueForParticipant(String oncHistoryDetailId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            PreparedStatement stmt = null;
            try {
                stmt = conn.prepareStatement(INSERT_TISSUE, Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, oncHistoryDetailId);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                dbVals.resultValue = getPrimaryKey(rs, "tissue_id");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return dbVals;
        });

        return (String) results.resultValue;
    }

    public String createSmIdForParticipant(String tissueId, String smIdValue) {
        String smIdUssType = new TissueSMIDDao().getTypeForName("uss");
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            PreparedStatement stmt = null;
            try {
                stmt = conn.prepareStatement(INSERT_USS_SM_ID, Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, tissueId);
                stmt.setString(2, smIdValue);
                stmt.setString(3, smIdUssType);
                stmt.setLong(4, System.currentTimeMillis());
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                dbVals.resultValue = getPrimaryKey(rs, "sm_id_pk");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return dbVals;
        });

        return (String) results.resultValue;
    }

    public void deleteSmId(List<String> smIdPKs) {
        for (String smIdPk : smIdPKs) {
            try {
                delete("sm_id", "sm_id_pk", smIdPk);
            } catch (Exception e) {
                log.error("unable to delete sm id {}", smIdPk, e);
            }
        }
    }

    public void deleteTissue(List<String> tissueIds) {
        for (String tissueId : tissueIds) {
            try {
                delete("ddp_tissue", "tissue_id", tissueId);
            } catch (Exception e) {
                log.error("unable to delete tissue {}", tissueId, e);
            }
        }
    }

    public void deleteOncHistoryDetailAndMedicalRecord(List<String> oncHistoryDetailIds, List<String> ddpParticipantIds) {
        for (String oncHistoryDetailId : oncHistoryDetailIds) {
            try {
                String ddpParticipantId = ddpParticipantIds.get(0);
                OncHistoryDetail oncHistoryDetail =
                        OncHistoryDetail.getOncHistoryDetailsByParticipantIds(instanceName, ddpParticipantIds).get(ddpParticipantId)
                                .get(0);
                String medicalRecordId = String.valueOf(oncHistoryDetail.getMedicalRecordId());
                String institutionId =
                        MedicalRecord.getMedicalRecord(instanceName, ddpParticipantId, medicalRecordId).getDdpInstitutionId();

                delete("ddp_onc_history_detail", "onc_history_detail_id", oncHistoryDetailId);
                delete("ddp_medical_record", "medical_record_id", medicalRecordId);
                delete("ddp_institution", "institution_id", institutionId);
                delete("ddp_participant", "ddp_participant_id", ddpParticipantId);
            } catch (Exception e) {
                log.error("unable to delete onc history {}", oncHistoryDetailId, e);
            }
        }
    }

    public String createInstitutionForParticipant(String participantId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            PreparedStatement stmt = null;
            try {
                stmt = conn.prepareStatement(INSERT_INSTITUTION, Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, participantId);
                stmt.setString(2, UUID.randomUUID().toString());
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                dbVals.resultValue = getPrimaryKey(rs, "ddp_institution_id");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return dbVals;
        });

        return (String) results.resultValue;
    }

    public String createMRForParticipant(String institutionId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            PreparedStatement stmt = null;
            try {
                stmt = conn.prepareStatement(INSERT_MEDICAL_RECORD, Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, institutionId);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                dbVals.resultValue = getPrimaryKey(rs, "medical_record_id");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return dbVals;
        });

        return (String) results.resultValue;
    }
}
