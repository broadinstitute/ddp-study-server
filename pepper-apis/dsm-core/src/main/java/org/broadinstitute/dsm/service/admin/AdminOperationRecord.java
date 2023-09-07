package org.broadinstitute.dsm.service.admin;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;

import org.broadinstitute.dsm.exception.DsmInternalError;

public class AdminOperationRecord {

    public enum OperationStatus {
        STARTED,
        COMPLETED
    }

    private static final String SQL_INSERT_OPERATION =
            "INSERT INTO admin_operation SET operation_type_id = ?, operator_id = ?, operation_start = ?, status = ?";
    private static final String SQL_UPDATE_OPERATION =
            "UPDATE admin_operation SET operation_end = ?, status = ?, results = ? WHERE operation_id = ?";

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
}
