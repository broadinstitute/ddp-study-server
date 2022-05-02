package org.broadinstitute.dsm.db.dao.ddp.onchistory;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

public class OncHistoryDetailDaoImpl implements OncHistoryDetailDao<OncHistoryDetailDto> {

    public static final String SQL_SELECT_TISSUE_RECEIVED =
            "SELECT tissue_received FROM ddp_onc_history_detail WHERE onc_history_detail_id = ?";

    @Override
    public int create(OncHistoryDetailDto oncHistoryDetailDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<OncHistoryDetailDto> get(long id) {
        return Optional.empty();
    }

    @Override
    public boolean hasReceivedDate(int oncHistoryDetailId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_TISSUE_RECEIVED)) {
                stmt.setInt(1, oncHistoryDetailId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String receivedDate = rs.getString(DBConstants.TISSUE_RECEIVED);
                        if (StringUtils.isNotBlank(receivedDate)) {
                            dbVals.resultValue = true;
                        } else {
                            dbVals.resultValue = false;
                        }
                    } else {
                        dbVals.resultException = new RuntimeException(" The onc history detail id was not found in the table!");
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
            throw new RuntimeException(" Error getting the received date of the OncHistoryDetail with Id:" + oncHistoryDetailId,
                    results.resultException);
        }
        return (Boolean) results.resultValue;
    }
}
