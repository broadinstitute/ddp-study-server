package org.broadinstitute.dsm.db.dao.ddp.instance;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

public class GroupDao implements Dao<String> {
    private static final String SQL_INSERT_GROUP = "INSERT INTO ddp_group SET name = ? ";
    private static final String SQL_SELECT_GROUP_ID_BY_NAME = "SELECT * FROM ddp_group WHERE group_id = ? ";
    private static final String SQL_SELECT_GROUP_NAME_BY_ID = "SELECT * FROM ddp_group WHERE name = ? ";
    private static final String SQL_DELETE_GROUP = "DELETE FROM ddp_group WHERE group_id = ? ";


    @Override
    public int create(String groupName) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_GROUP, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, groupName);
                int r = stmt.executeUpdate();
                if (r != 1) {
                    throw new DsmInternalError("Group inserted more than one row, the updated number was " + r);
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultException)) {
            throw new DsmInternalError("Error inserting new group with name: " + groupName, results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_GROUP)) {
                stmt.setLong(1, id);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new DsmInternalError("Error deleting from ddp_group with id: " + id, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }

    @Override
    public Optional<String> get(long id) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_GROUP_ID_BY_NAME)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getString(DBConstants.DDP_GROUP_NAME);
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultException)) {
            throw new DsmInternalError("Error getting ddp_group for id: " + id, results.resultException);
        }
        return Optional.ofNullable((String) results.resultValue);
    }

    public int getGroupIdByName(String groupName) {
        SimpleResult results = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_GROUP_NAME_BY_ID)) {
                stmt.setString(1, groupName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(DBConstants.GROUP_ID);
                    } else {
                        dbVals.resultValue = -1;
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        if (Objects.nonNull(results.resultException)) {
            throw new DsmInternalError("Error getting ddp_group with name: " + groupName, results.resultException);
        }
        return (int) results.resultValue;
    }
}
