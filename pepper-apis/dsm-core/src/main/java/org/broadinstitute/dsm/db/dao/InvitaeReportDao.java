package org.broadinstitute.dsm.db.dao;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dto.invitae.InvitaeReport;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;

@Slf4j
public class InvitaeReportDao {
    private static final String SQL_INSERT_NEW_INVITAE =
            "INSERT INTO invitae_report (participant_id, last_changed, changed_by) values (?, ?, ?) ";

    private static final String SQL_GET_INVITAE_FOR_REALM = "SELECT * from invitae_report inv "
            + " left join ddp_participant p on (p.participant_id = inv.participant_id) "
            + " left join ddp_instance realm on (realm.ddp_instance_id = p.ddp_instance_id) "
            + " where realm.instance_name = ? ";

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

    public Map<String, Object> getInvitaeReportByStudy(String study) {
        Map<String, Object> invitaeReportMap = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_INVITAE_FOR_REALM)) {
                stmt.setString(1, study);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String ddpParticipantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        InvitaeReport invitaeReport = getInvitaeReport(rs);
                        if (Objects.isNull(invitaeReport)) {
                            continue;
                        }
                        invitaeReportMap.put(ddpParticipantId, invitaeReport);
                    }
                }
            } catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting invitae for " + study, results.resultException);
        }
        return invitaeReportMap;
    }

    private InvitaeReport getInvitaeReport(ResultSet rs) {
        try {
            return new InvitaeReport(rs.getString(DBConstants.INVITAE_REPORT_ID), rs.getString(DBConstants.INVITAE_REPORT_DATE),
                    rs.getString(DBConstants.INVITAE_BAM_FILE), rs.getString(DBConstants.INVITAE_BAM_FILE_DATE),
                    rs.getString(DBConstants.INVITAE_GERMLINE_NOTES), rs.getString(DBConstants.PARTICIPANT_ID));
        } catch (SQLException e) {
            throw new RuntimeException("Error getting the invitae from rs ", e);
        }
    }
}
