package org.broadinstitute.dsm.service.admin;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.broadinstitute.dsm.exception.DsmInternalError;

/**
 * Records and retrieves the results of running an admin operation
 */
public class AdminOperationRecord {

    public enum OperationStatus {
        STARTED,
        COMPLETED
    }

    private static final String SQL_INSERT_OPERATION =
            "INSERT INTO admin_operation SET operation_type_id = ?, operator_id = ?, operation_start = ?, status = ?";
    private static final String SQL_UPDATE_OPERATION =
            "UPDATE admin_operation SET operation_end = ?, status = ?, results = ? WHERE operation_id = ?";

    private static final String SQL_SELECT_OPERATION_BY_ID =
            "SELECT * FROM admin_operation WHERE operation_id = ?";

    // TODO for now just artificially limit since results may be large. Add pagination, etc. later as needed
    // by usage. (IOW, no need to over engineer this until we see actual usage and results size.) -DC
    private static final String SQL_SELECT_OPERATIONS_BY_TYPE_ID =
            "SELECT * FROM admin_operation WHERE operation_type_id = ? ORDER BY operation_start DESC LIMIT 10";

    public static int createOperationRecord(AdminOperationService.OperationTypeId operationTypeId, String userId) {
        Date date = new Date();
        Timestamp startTime = new Timestamp(date.getTime());

        return inTransaction(conn -> {
            int id = -1;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_OPERATION, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, operationTypeId.name());
                stmt.setString(2, userId);
                stmt.setTimestamp(3, startTime);
                stmt.setString(4, OperationStatus.STARTED.name());
                int result = stmt.executeUpdate();
                if (result != 1) {
                    throw new DsmInternalError("Error adding admin_operation. Result count was " + result);
                }
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                }
            } catch (SQLException ex) {
                throw new DsmInternalError("Error adding admin_operation", ex);
            }
            if (id == -1) {
                throw new DsmInternalError("Error adding admin_operation: key not returned");
            }
            return id;
        });
    }

    public static void updateOperationRecord(int operationId, OperationStatus status, String result) {
        Timestamp endTime;
        if (status.equals(OperationStatus.COMPLETED)) {
            Date date = new Date();
            endTime = new Timestamp(date.getTime());
        } else {
            endTime = null;
        }

        inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_OPERATION)) {
                stmt.setTimestamp(1, endTime);
                stmt.setString(2, status.name());
                stmt.setString(3, result);
                stmt.setInt(4, operationId);
                int res = stmt.executeUpdate();
                if (res != 1) {
                    throw new DsmInternalError("Error updating admin_operation. Result count was " + result);
                }
                return res;
            } catch (SQLException e) {
                throw new DsmInternalError("Error updating admin_operation for operation ID: " + operationId);
            }
        });
    }

    /**
     * Get operation record for operation ID
     * @return record or null if not found
     */
    public static AdminOperationResponse.AdminOperationResult getOperationRecord(int operationId) {
        return inTransaction(conn -> {
            AdminOperationResponse.AdminOperationResult result = null;
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_OPERATION_BY_ID)) {
                stmt.setInt(1, operationId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        result = getResult(rs);
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting admin operation result for operation " + operationId, e);
            }
            return result;
        });
    }

    /**
     * Get operation records for operation type ID
     * @return records or empty list if not found
     */
    public static List<AdminOperationResponse.AdminOperationResult> getOperationTypeRecords(String operationTypeId) {
        return inTransaction(conn -> {
            List<AdminOperationResponse.AdminOperationResult> results = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_OPERATION_BY_ID)) {
                stmt.setString(1, operationTypeId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(getResult(rs));
                    }
                }
            } catch (SQLException e) {
                throw new DsmInternalError("Error getting admin operation results for operation type " + operationTypeId, e);
            }
            return results;
        });

    }

    private static AdminOperationResponse.AdminOperationResult getResult(ResultSet rs) throws SQLException {
        return new AdminOperationResponse.AdminOperationResult.Builder()
                .withOperationId(rs.getInt("operation_id"))
                .withOperationTypeId(rs.getString("operation_type_id"))
                .withOperatorId(rs.getString("operator_id"))
                .withOperationStart(rs.getTimestamp("operation_start"))
                .withOperationEnd(rs.getTimestamp("operation_end"))
                .withStatus(rs.getString("status"))
                .withResults(rs.getString("results")).build();
    }
}
