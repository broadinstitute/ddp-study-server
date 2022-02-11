package org.broadinstitute.dsm.db.dao.ddp.tissue;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.statics.DBConstants;

public class TissueDao implements Dao<Tissue> {


    private static final String SQL_SELECT_TISSUES_BY_STUDY = "SELECT p.ddp_participant_id, " +
            "tissue_id, t.onc_history_detail_id, t.notes, count_received, tissue_type, tissue_site, tumor_type, h_e, pathology_report, " +
            "collaborator_sample_id, " +
            "block_sent, scrolls_received, sk_id, sm_id, sent_gp, first_sm_id, additional_tissue_value_json, expected_return, return_date, " +
            "return_fedex_id, shl_work_number, tumor_percentage, tissue_sequence, scrolls_count, uss_count, h_e_count, blocks_count " +
            "FROM ddp_onc_history_detail oD " +
            "LEFT JOIN ddp_medical_record m on (oD.medical_record_id = m.medical_record_id AND NOT oD.deleted <=> 1 AND NOT m.deleted <=> 1) " +
            "LEFT JOIN ddp_institution inst on (inst.institution_id = m.institution_id) " +
            "LEFT JOIN ddp_participant p on (p.participant_id = inst.participant_id) " +
            "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_tissue t on (oD.onc_history_detail_id = t.onc_history_detail_id AND NOT t.deleted <=> 1) " +
            "WHERE realm.instance_name = ? ";

    @Override
    public int create(Tissue tissue) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<Tissue> get(long id) {
        return Optional.empty();
    }

    public Map<String, List<Tissue>> getTissuesByStudy(String study) {
        Map<String, List<Tissue>> tissues = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_TISSUES_BY_STUDY)) {
                stmt.setString(1, study);
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        Tissue tissue = Tissue.getTissue(rs);
                        if (Objects.isNull(tissue)) continue;
                        tissues.merge(ddpParticipantId, new ArrayList<>(List.of(tissue)), (prev, curr) -> {
                            prev.addAll(curr);
                            return prev;
                        });
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting tissues for "
                    + study, results.resultException);
        }
        return tissues;
    }
}
