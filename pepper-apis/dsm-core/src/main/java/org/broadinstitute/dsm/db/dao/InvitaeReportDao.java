package org.broadinstitute.dsm.db.dao;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class InvitaeReportDao {
    private static final String SQL_INSERT_NEW_INVITAE =
            "INSERT INTO invitae_report (participant_id, last_changed, changed_by) values (?, ?, ?) ";

    public static String createNewInvitaeReport(String participantId, String user) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_NEW_INVITAE, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, participantId);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, user);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            String invitaeReportId = rs.getString(1);
                            log.info("Added new InvitaeReport for medicalRecord w/ id " + participantId);
                            dbVals.resultValue = invitaeReportId;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error getting id of new InvitaeReport ", e);
                    }
                } else {
                    throw new RuntimeException(
                            "Error adding new InvitaeReport for participant w/ id " + participantId + " it was updating " + result
                                    + " rows");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error adding new InvitaeReport for participant w/ id " + participantId,
                    results.resultException);
        } else {
            return (String) results.resultValue;
        }
    }
}
