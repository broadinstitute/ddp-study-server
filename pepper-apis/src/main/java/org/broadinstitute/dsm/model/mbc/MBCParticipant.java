package org.broadinstitute.dsm.model.mbc;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.jruby.embed.ScriptingContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

@Data
public class MBCParticipant {

    private static final Logger logger = LoggerFactory.getLogger(MBCParticipant.class);

    private String participantId;
    private String firstName;
    private String lastName;
    private String diagnosedMonth;
    private String diagnosedYear;
    private String dOBConsent;
    private String dOBBlood;
    private String country;
    private String updatedAt;

    public MBCParticipant(String participantId, String firstName, String lastName, String country, String diagnosedMonth,
                          String diagnosedYear, String dOBConsent, String dOBBlood, String updatedAt) {
        this.participantId = participantId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.country = country;
        this.diagnosedMonth = diagnosedMonth;
        this.diagnosedYear = diagnosedYear;
        this.dOBConsent = dOBConsent;
        this.dOBBlood = dOBBlood;
        this.updatedAt = updatedAt;

    }

    public static void getParticipantsFromDB(@NonNull String ddpInstanceName, @NonNull String url, @NonNull ScriptingContainer container, @NonNull Object receiver) {
        logger.info("Getting participant information for " + ddpInstanceName);
        Map<String, MBCParticipant> mbcData = new HashMap<>();
        Connection conn = null;
        try {
            if (Boolean.valueOf(TransactionWrapper.getSqlFromConfig(ddpInstanceName.toLowerCase() + "." + MBC.POSTGRESQL))) {
                Class.forName("org.postgresql.Driver");
                conn = DriverManager.getConnection(url);
                int counter = 0;
                try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_PARTICIPANTS_MBC))) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String participantId = rs.getString(DBConstants.ID);
                            mbcData.put(participantId, new MBCParticipant(participantId,
                                    rs.getString(DBConstants.ENCRYPTED_FIRST_NAME),
                                    rs.getString(DBConstants.ENCRYPTED_LAST_NAME),
                                    rs.getString(DBConstants.ENCRYPTED_COUNTRY),
                                    rs.getString(DBConstants.ENCRYPTED_DIAGNOSED_AT_MONTH),
                                    rs.getString(DBConstants.ENCRYPTED_DIAGNOSED_AT_YEAR),
                                    rs.getString(DBConstants.ENCRYPTED_BIRTHDAY),
                                    rs.getString(DBConstants.ENCRYPTED_BD_BIRTHDAY),
                                    rs.getString(DBConstants.UPDATED_AT)));
                            counter++;
                        }
                        logger.info("Got " + counter + " participants from " + ddpInstanceName);
                    }
                }
                catch (SQLException ex) {
                    logger.error("Error getting participants from mbc ", ex);
                }
            }
        }
        catch (ClassNotFoundException e) {
            logger.warn(e.toString());
        }
        catch (SQLException e) {
            logger.warn(e.toString());
        }
        finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            }
            catch (SQLException e) {
                logger.error("Couldn't close connection to db ", e);
            }
        }
        logger.info("Finished getting participants from MBC DB");
        //decrypt data
        String key = TransactionWrapper.getSqlFromConfig(ddpInstanceName.toLowerCase() + "." + MBC.ENCRYPTION_KEY);
        for (MBCParticipant participant : mbcData.values()) {
            DSMServer.putMBCParticipant(participant.getParticipantId(), new MBCParticipant(participant.getParticipantId(),
                    MBC.decryptValue(container, receiver, key, participant.getFirstName()),
                    MBC.decryptValue(container, receiver, key, participant.getLastName()),
                    MBC.decryptValue(container, receiver, key, participant.getCountry()),
                    MBC.decryptValue(container, receiver, key, participant.getDiagnosedMonth()),
                    MBC.decryptValue(container, receiver, key, participant.getDiagnosedYear()),
                    MBC.decryptValue(container, receiver, key, participant.getDOBConsent()),
                    MBC.decryptValue(container, receiver, key, participant.getDOBBlood()),
                    participant.getUpdatedAt()));
        }
        logger.info("Finished decryption of participants from MBC");
    }
}
