package org.broadinstitute.dsm.model;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class TissueList {
    public static final Logger logger = LoggerFactory.getLogger(TissueList.class);
    public static final String SQL_SELECT_ALL_ONC_HISTORY_TISSUE_FOR_REALM = "SELECT p.ddp_participant_id," +
            "oD.onc_history_detail_id, oD.request, oD.deleted, oD.fax_sent, oD.tissue_received, oD.medical_record_id, oD.date_px, oD.type_px, " +
            "oD.location_px, oD.histology, oD.accession_number, oD.facility, oD.phone, oD.fax, oD.notes, oD.additional_values_json, " +
            "oD.request, oD.fax_sent, oD.fax_sent_by, oD.fax_confirmed, oD.fax_sent_2, oD.fax_sent_2_by, oD.fax_confirmed_2, oD.fax_sent_3, " +
            "oD.fax_sent_3_by, oD.fax_confirmed_3, oD.tissue_received, oD.tissue_problem_option, oD.additional_values_json, oD.gender, oD.destruction_policy, oD.unable_obtain_tissue," +
            "tissue_id, t.notes, t.count_received, t.tissue_type, t.tissue_site, t.tumor_type, t.h_e, t.pathology_report, t.collaborator_sample_id, t.block_sent, t.scrolls_received, t.sk_id, t.sm_id, " +
            "t.sent_gp, t.first_sm_id, t.additional_tissue_value_json, t.expected_return, t.return_date, t.return_fedex_id, t.shl_work_number, t.tumor_percentage, t.tissue_sequence, t.onc_history_detail_id, " +
            "t.scrolls_count, t.h_e_count, t.uss_count, t.blocks_count " +
            "FROM ddp_participant p LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_participant_exit ex on (p.ddp_participant_id = ex.ddp_participant_id AND p.ddp_instance_id = ex.ddp_instance_id) " +
            "LEFT JOIN ddp_institution inst on (p.participant_id = inst.participant_id) LEFT JOIN ddp_medical_record m on (m.institution_id = inst.institution_id AND NOT m.deleted <=> 1) " +
            "LEFT JOIN ddp_onc_history_detail oD on (m.medical_record_id = oD.medical_record_id AND NOT oD.deleted <=> 1) " +
            "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id AND NOT t.deleted <=> 1) WHERE realm.instance_name = ? AND ex.ddp_participant_exit_id IS NULL AND oD.onc_history_detail_id IS NOT NULL";
    public static final String SQL_ORDER_BY_ONC_HISTORY = " ORDER BY oD.onc_history_detail_id ";

    private OncHistoryDetail oncHistoryDetails;
    private Tissue tissue;
    private String ddpParticipantId;

    public TissueList(OncHistoryDetail OncHistoryDetails, Tissue tissue, String ddpParticipantId) {
        this.oncHistoryDetails = OncHistoryDetails;
        this.tissue = tissue;
        this.ddpParticipantId = ddpParticipantId;

    }

    public static List<TissueList> getAllTissueListsForRealmNoFilter(String realm) {
        return getAllTissueListsForRealm(realm, SQL_SELECT_ALL_ONC_HISTORY_TISSUE_FOR_REALM + SQL_ORDER_BY_ONC_HISTORY);
    }

    public static List<TissueList> getAllTissueListsForRealm(String realm, String query) {
        List<TissueList> results = new ArrayList<>();
        SimpleResult result = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, realm);
                try (ResultSet rs = stmt.executeQuery()) {
                    OncHistoryDetail oncHistory = null;
                    String ptId = null;
                    while (rs.next()) {
                        oncHistory = OncHistoryDetail.getOncHistoryDetail(rs);
                        ptId = rs.getString(DBConstants.DDP_PARTICIPANT_ALIAS + DBConstants.ALIAS_DELIMITER + DBConstants.DDP_PARTICIPANT_ID);
                        Tissue tissue = Tissue.getTissue(rs);
                        TissueList tissueList = new TissueList(oncHistory, null, ptId);

                        if (!tissue.isTDeleted() && StringUtils.isNotBlank(tissue.getTissueId())) {
                            tissueList.setTissue(tissue);
                        }
                        results.add(tissueList);
                    }
                    dbVals.resultValue = results;
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            catch (Exception e) {
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
