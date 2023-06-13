package org.broadinstitute.dsm.db.dao.ddp.onchistory;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dao.util.DaoUtil;
import org.broadinstitute.dsm.db.dao.util.ResultsBuilder;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

public class OncHistoryDao implements Dao<OncHistoryDto> {

    public static final String ONC_HISTORY_ID = "onc_history_id";
    public static final String PARTICIPANT_ID = "participant_id";
    public static final String CREATED = "created";
    public static final String REVIEWED = "reviewed";
    public static final String ONC_HISTORY_LAST_CHANGED = "last_changed";
    public static final String ONC_HISTORY_CHANGED_BY = "changed_by";

    private static final String SQL_SELECT_ONC_HISTORIES_BY_STUDY =
            "SELECT p.participant_id, p.ddp_participant_id, o.onc_history_id, o.created, o.reviewed FROM ddp_participant p "
                    + "LEFT JOIN ddp_instance realm on (p.ddp_instance_id = realm.ddp_instance_id) "
                    + "LEFT JOIN ddp_onc_history o on (o.participant_id = p.participant_id) WHERE realm.instance_name = ? ";

    private static final String SQL_SELECT_BY_PARTICIPANT_ID = "SELECT * FROM ddp_onc_history WHERE participant_id = ?;";

    private static final String SQL_INSERT = "INSERT INTO ddp_onc_history "
            + "(participant_id, created, reviewed, last_changed, changed_by) VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_DELETE_BY_ID = "DELETE FROM ddp_onc_history WHERE onc_history_id = ?";

    @Override
    public int create(OncHistoryDto oncHistoryDto) {
        SimpleResult res = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, oncHistoryDto.getParticipantId());
                stmt.setString(2, oncHistoryDto.getCreated());
                stmt.setString(3, oncHistoryDto.getReviewed());
                stmt.setLong(4, oncHistoryDto.getLastChanged());
                stmt.setString(5, oncHistoryDto.getChangedBy());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });
        if (res.resultException != null || (int)res.resultValue == -1) {
            String msg = String.format("Error creating onc history record for participant %d",
                    oncHistoryDto.getParticipantId());
            if (res.resultException != null) {
                throw new DsmInternalError(msg, res.resultException);
            }
            throw new DsmInternalError(msg);
        }
        return (int) res.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = DaoUtil.deleteById(id, SQL_DELETE_BY_ID);
        if (simpleResult.resultException != null) {
            throw new DsmInternalError("Error deleting onc history record with id: " + id,
                    simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<OncHistoryDto> get(long id) {
        return Optional.empty();
    }

    public static Optional<OncHistoryDto> getByParticipantId(int id) {
        OncHistoryDao.BuildOncHistoryDto builder = new OncHistoryDao.BuildOncHistoryDto();
        SimpleResult res = DaoUtil.getById(id, SQL_SELECT_BY_PARTICIPANT_ID, builder);
        if (res.resultException != null) {
            throw new RuntimeException("Error getting onc history with participant id: " + id,
                    res.resultException);
        }
        return (Optional<OncHistoryDto>) res.resultValue;
    }

    private static class BuildOncHistoryDto implements ResultsBuilder {
        public Object build(ResultSet rs) throws SQLException {
            return new OncHistoryDto.Builder()
                    .withOncHistoryId(rs.getInt(ONC_HISTORY_ID))
                    .withParticipantId(rs.getInt(PARTICIPANT_ID))
                    .withCreated(rs.getString(CREATED))
                    .withReviewed(rs.getString(REVIEWED))
                    .withLastChanged(rs.getLong(ONC_HISTORY_LAST_CHANGED))
                    .withChangedBy(rs.getString(ONC_HISTORY_CHANGED_BY)).build();
        }
    }

    public Map<String, OncHistoryDto> getOncHistoriesByStudy(String study) {
        Map<String, OncHistoryDto> oncHistories = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_ONC_HISTORIES_BY_STUDY)) {
                stmt.setString(1, study);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        OncHistoryDto oncHistoryDto =
                                new OncHistoryDto(rs.getInt(ONC_HISTORY_ID), rs.getInt(PARTICIPANT_ID), rs.getString(CREATED),
                                        rs.getString(REVIEWED));
                        oncHistories.put(ddpParticipantId, oncHistoryDto);
                    }
                }
            } catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting onc histories for " + study, results.resultException);
        }
        return oncHistories;
    }
}
