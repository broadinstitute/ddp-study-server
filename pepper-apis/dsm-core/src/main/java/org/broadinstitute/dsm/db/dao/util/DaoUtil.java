package org.broadinstitute.dsm.db.dao.util;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.lddp.db.SimpleResult;

public class DaoUtil {

    private DaoUtil() {}

    /**
     * Get a record by ID
     *
     * @param id record ID, must be in first substitution position of query
     * @param query get query
     */
    public static SimpleResult getById(long id, String query, ResultsBuilder resultsBuilder) {
        return inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = resultsBuilder.build(rs);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
    }

    /**
     * Delete a record by ID
     *
     * @param id record ID, must be in first substitution position of query
     * @param query delete query
     */
    public static SimpleResult deleteById(int id, String query) {
        return inTransaction(conn -> {
            SimpleResult res = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, id);
                res.resultValue = stmt.executeUpdate();
            } catch (SQLException e) {
                res.resultException = e;
            }
            return res;
        });
    }
}
