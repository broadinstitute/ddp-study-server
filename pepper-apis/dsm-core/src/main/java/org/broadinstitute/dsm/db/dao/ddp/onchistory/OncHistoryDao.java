package org.broadinstitute.dsm.db.dao.ddp.onchistory;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.statics.DBConstants;

public class OncHistoryDao implements Dao<OncHistoryDto> {

    private static final String SQL_SELECT_ONC_HISTORIES_BY_STUDY = "SELECT p.participant_id, p.ddp_participant_id, " +
            "o.onc_history_id, o.created, o.reviewed " +
            "FROM ddp_participant p " +
            "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) " +
            "LEFT JOIN ddp_onc_history o on (o.participant_id = p.participant_id) " +
            "WHERE realm.instance_name = ? ";
    public static final String ONC_HISTORY_ID = "onc_history_id";
    public static final String PARTICIPANT_ID = "participant_id";
    public static final String CREATED = "created";
    public static final String REVIEWED = "reviewed";

    @Override
    public int create(OncHistoryDto oncHistoryDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<OncHistoryDto> get(long id) {
        return Optional.empty();
    }

    public Map<String, OncHistoryDto> getOncHistoriesByStudy(String study) {
        Map<String, OncHistoryDto> oncHistories = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ONC_HISTORIES_BY_STUDY)) {
                stmt.setString(1, study);
                try(ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        OncHistoryDto oncHistoryDto = new OncHistoryDto(
                                rs.getInt(ONC_HISTORY_ID),
                                rs.getInt(PARTICIPANT_ID),
                                rs.getString(CREATED),
                                rs.getString(REVIEWED)
                        );
                        oncHistories.put(ddpParticipantId, oncHistoryDto);
                    }
                }
            }
            catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting onc histories for "
                    + study, results.resultException);
        }
        return oncHistories;
    }
}
