package org.broadinstitute.dsm.model;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lombok.Data;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class TissueList {
    public static final Logger logger = LoggerFactory.getLogger(TissueList.class);
    public static final String SQL_SELECT_ALL_ONC_HISTORY_TISSUE_FOR_REALM =
            "SELECT p.ddp_participant_id, p.ddp_instance_id, p.participant_id, p.assignee_id_tissue,"
                    + "oD.onc_history_detail_id, oD.request, oD.fax_sent, oD.tissue_received, oD.medical_record_id, "
                    + "oD.date_px, oD.type_px, "
                    + "oD.location_px, oD.histology, oD.accession_number, oD.facility, oD.phone, oD.fax, oD.notes, "
                    + "oD.additional_values_json, "
                    + "oD.request, oD.fax_sent, oD.fax_sent_by, oD.fax_confirmed, oD.fax_sent_2, oD.fax_sent_2_by, oD.fax_confirmed_2, "
                    + "oD.fax_sent_3, oD.fax_sent_3_by, oD.fax_confirmed_3, oD.tissue_received, oD.tissue_problem_option, "
                    + "oD.additional_values_json, oD.gender, oD.destruction_policy, oD.unable_obtain_tissue,"
                    + "t.tissue_id, t.notes, t.count_received, t.tissue_type, t.tissue_site, t.tumor_type, t.h_e, t.pathology_report, "
                    + "t.collaborator_sample_id, t.block_sent, t.scrolls_received, t.sk_id, t.sm_id, "
                    + "t.sent_gp, t.first_sm_id, t.additional_tissue_value_json, t.expected_return, t.return_date, t.return_fedex_id, "
                    + "t.shl_work_number, t.block_id_shl, t.tumor_percentage, t.tissue_sequence, t"
                    + ".onc_history_detail_id, "
                    + "t.scrolls_count, t.h_e_count, t.uss_count, t.blocks_count, "
                    + "sm.sm_id_value, sm.sm_id_type_id, sm.sm_id_pk, sm.tissue_id, smt.sm_id_type "
                    + "FROM ddp_participant p LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) "
                    + "LEFT JOIN ddp_participant_exit ex on (p.ddp_participant_id = ex.ddp_participant_id "
                    + "AND p.ddp_instance_id = ex.ddp_instance_id) "
                    + "LEFT JOIN ddp_institution inst on (p.participant_id = inst.participant_id) "
                    + "LEFT JOIN ddp_medical_record m on (m.institution_id = inst.institution_id AND NOT m.deleted <=> 1) "
                    + "LEFT JOIN ddp_onc_history_detail oD on (m.medical_record_id = oD.medical_record_id) "
                    + "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id) "
                    + "LEFT JOIN sm_id sm on (sm.tissue_id = t.tissue_id) "
                    + "LEFT JOIN sm_id_type smt on (smt.sm_id_type_id = sm.sm_id_type_id ) "
                    + "WHERE realm.instance_name = ? AND ex.ddp_participant_exit_id IS NULL AND oD.onc_history_detail_id IS NOT NULL";
    public static final String SQL_ORDER_BY_ONC_HISTORY = " ORDER BY oD.onc_history_detail_id ";

    private OncHistoryDetail oncHistoryDetails;
    private Tissue tissue;
    private String ddpParticipantId;
    private String participantId;
    private Participant participant;

    public TissueList(OncHistoryDetail oncHistoryDetail, Tissue tissue, String ddpParticipantId) {
        this.oncHistoryDetails = oncHistoryDetail;
        this.tissue = tissue;
        this.ddpParticipantId = ddpParticipantId;

    }

    public TissueList(OncHistoryDetail oncHistoryDetails, Tissue tissue, String ddpParticipantId, String participantId,
                      Participant participant) {
        this.oncHistoryDetails = oncHistoryDetails;
        this.tissue = tissue;
        this.ddpParticipantId = ddpParticipantId;
        this.participantId = participantId;
        this.participant = participant;

    }

    public static List<TissueList> getAllTissueListsForRealmNoFilter(String realm) {
        return getAllTissueListsForRealm(realm, SQL_SELECT_ALL_ONC_HISTORY_TISSUE_FOR_REALM + SQL_ORDER_BY_ONC_HISTORY);
    }

    public static List<TissueList> getAllTissueListsForRealm(String realm, String query) {
        List<TissueList> results = new ArrayList<>();
        HashMap<Integer, Tissue> tissues = new HashMap<>();
        HashMap<Integer, OncHistoryDetail> oncHistoryDetailHashMap = new HashMap<>();
        HashMap<String, Participant> participantHashMap = new HashMap<>();
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String participantId =
                                rs.getString(DBConstants.DDP_PARTICIPANT_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.PARTICIPANT_ID);
                        String ddpParticipantId = rs.getString(
                                DBConstants.DDP_PARTICIPANT_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.DDP_PARTICIPANT_ID);
                        int ddpInstanceId = rs.getInt(
                                DBConstants.DDP_PARTICIPANT_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.DDP_INSTANCE_ID);
                        String assigneeIdTissue = rs.getString(
                                DBConstants.DDP_PARTICIPANT_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.ASSIGNEE_ID_TISSUE);
                        participantHashMap.put(ddpParticipantId,
                                new Participant(participantId, ddpParticipantId, ddpInstanceId, assigneeIdTissue));
                        SmId tissueSmId = Tissue.getSMIds(rs);
                        Tissue tissue;
                        if (tissueSmId != null && tissues.containsKey(tissueSmId.getTissueId())) {
                            tissue = tissues.get(tissueSmId.getTissueId());
                        } else {
                            tissue = Tissue.getTissue(rs);
                        }
                        OncHistoryDetail oncHistory = OncHistoryDetail.getOncHistoryDetail(rs);
                        if (tissue != null) {
                            if (!oncHistoryDetailHashMap.containsKey(tissue.getOncHistoryDetailId())) {
                                oncHistoryDetailHashMap.put(oncHistory.getOncHistoryDetailId(), oncHistory);
                            }
                            if (tissueSmId != null) {
                                tissue.setSmIdBasedOnType(tissueSmId, rs);
                            }
                            if (tissue.getTissueId() != null) {
                                tissues.put(tissue.getTissueId(), tissue);
                            }
                        } else if (!oncHistoryDetailHashMap.containsKey(oncHistory.getOncHistoryDetailId())) {
                            //add oncHistoryDetail even if it doesn't have tissue
                            oncHistoryDetailHashMap.put(oncHistory.getOncHistoryDetailId(), oncHistory);
                        }
                    }
                    for (Tissue tissue : tissues.values()) {
                        Integer tissueOncHistoryDetailId = tissue.getOncHistoryDetailId();
                        OncHistoryDetail oncHistoryDetail = oncHistoryDetailHashMap.get(tissueOncHistoryDetailId);
                        oncHistoryDetail.getTissues().add(tissue);
                    } //  add onchistories to their particiapnt
                    for (OncHistoryDetail oncHistoryDetail : oncHistoryDetailHashMap.values()) {
                        TissueList tissueList = new TissueList(oncHistoryDetail, null, oncHistoryDetail.getDdpParticipantId(),
                                oncHistoryDetail.getParticipantId(), participantHashMap.get(oncHistoryDetail.getDdpParticipantId()));
                        if (oncHistoryDetail.getTissues() == null || oncHistoryDetail.getTissues().isEmpty()) {
                            results.add(tissueList);
                            continue;
                        }
                        for (Tissue tissue : oncHistoryDetail.getTissues()) {
                            if (tissue.getTissueId() != null) {
                                tissueList.setTissue(tissue);
                            }
                            results.add(tissueList);
                        }
                    }
                    dbVals.resultValue = results;

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (result.resultException != null) {
            throw new RuntimeException(result.resultException);
        }
        List<TissueList> finalResult = (List<TissueList>) result.resultValue;
        logger.info("Got " + finalResult.size() + " TissueLists");
        return finalResult;
    }
}
