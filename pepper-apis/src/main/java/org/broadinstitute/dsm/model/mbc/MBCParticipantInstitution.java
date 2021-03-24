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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class  MBCParticipantInstitution {

    private static final Logger logger = LoggerFactory.getLogger(MBCParticipantInstitution.class);

    private final MBCParticipant mbcParticipant;
    private final MBCInstitution mbcInstitution;

    private static SimpleDateFormat sdf  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public MBCParticipantInstitution(MBCParticipant mbcParticipant, MBCInstitution mbcInstitution) {
        this.mbcParticipant = mbcParticipant;
        this.mbcInstitution = mbcInstitution;
    }

    public static Map<String, MBCParticipantInstitution> getPhysiciansFromDB(@NonNull String ddpInstanceName, @NonNull String url, long value, long timeLastCheck,
                                                                             @NonNull ScriptingContainer container, @NonNull Object receiver, boolean alreadyAddedOnServerStart) {
        Map<String, MBCParticipantInstitution> mbcDataChanges = new HashMap<>();
        Connection conn = null;
        try {
            if (Boolean.valueOf(TransactionWrapper.getSqlFromConfig(ddpInstanceName + "." + MBC.POSTGRESQL))) {
                Class.forName("org.postgresql.Driver");
                conn = DriverManager.getConnection(url);
                int counter = 0;
                try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_NEW_PHYSICIANS_MBC))) {
                    stmt.setLong(1, value);
                    stmt.setLong(2, timeLastCheck);
                    stmt.setLong(3, timeLastCheck);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String participantId = rs.getString(DBConstants.USER_ID);
                            String physicianId = rs.getString(DBConstants.ID);
                            String institutionLastChanged = rs.getString(DBConstants.PHY_UPDATED_AT);
                            Long updatedAt = null;
                            try {
                                Date date = sdf.parse(institutionLastChanged);
                                updatedAt = date.getTime();
                            }
                            catch (ParseException e) {
                                logger.warn("Couldn't parse physician update_at to date");
                            }

                            MBCParticipant participant = new MBCParticipant(participantId,
                                    rs.getString(DBConstants.ENCRYPTED_FIRST_NAME),
                                    rs.getString(DBConstants.ENCRYPTED_LAST_NAME),
                                    rs.getString(DBConstants.ENCRYPTED_COUNTRY),
                                    rs.getString(DBConstants.ENCRYPTED_DIAGNOSED_AT_MONTH),
                                    rs.getString(DBConstants.ENCRYPTED_DIAGNOSED_AT_YEAR),
                                    rs.getString(DBConstants.ENCRYPTED_BIRTHDAY),
                                    rs.getString(DBConstants.ENCRYPTED_BD_BIRTHDAY),
                                    rs.getString(DBConstants.PT_UPDATED_AT));
                            MBCInstitution institution = new MBCInstitution(physicianId,
                                    rs.getString(DBConstants.ENCRYPTED_NAME),
                                    rs.getString(DBConstants.ENCRYPTED_PHONE),
                                    rs.getString(DBConstants.ENCRYPTED_STREET),
                                    rs.getString(DBConstants.ENCRYPTED_CITY),
                                    rs.getString(DBConstants.ENCRYPTED_STATE),
                                    rs.getString(DBConstants.ENCRYPTED_ZIP),
                                    rs.getString(DBConstants.ENCRYPTED_INSTITUTION),
                                    institutionLastChanged,
                                    rs.getBoolean(DBConstants.IS_BLOOD_RELEASE),
                                    updatedAt != null && timeLastCheck < updatedAt ? true :  false);
                            mbcDataChanges.put(physicianId, new MBCParticipantInstitution(participant, institution));
                            counter++;
                        }
                        logger.info("Got " + counter + " new/updated physicians from " + ddpInstanceName + " bookmark was " + value + " lastTimeChecked " + timeLastCheck);
                    }
                }
                catch (SQLException ex) {
                    logger.error("Error getting institutions from mbc ", ex);
                }
            }
            else {
                logger.warn(ddpInstanceName + " does not use postgresql DB");
            }
        }
        catch (ClassNotFoundException e) {
            logger.error("Driver class for postgresql not found");
        }
        catch (SQLException e) {
            logger.error("SQL exception while trying to get institutions from postgresql db ", e);
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
        if (!alreadyAddedOnServerStart) {
            //decrypt data
            String key = TransactionWrapper.getSqlFromConfig(ddpInstanceName.toLowerCase() + "." + MBC.ENCRYPTION_KEY);
            logger.info("Got " + mbcDataChanges.size() + " rows of changes from MBC");
            for (MBCParticipantInstitution changes : mbcDataChanges.values()) {
                MBCParticipant participant = changes.getMbcParticipant();
                DSMServer.putMBCParticipant(participant.getParticipantId(), new MBCParticipant(participant.getParticipantId(),
                        MBC.decryptValue(container, receiver, key, participant.getFirstName()),
                        MBC.decryptValue(container, receiver, key, participant.getLastName()),
                        MBC.decryptValue(container, receiver, key, participant.getCountry()),
                        MBC.decryptValue(container, receiver, key, participant.getDiagnosedMonth()),
                        MBC.decryptValue(container, receiver, key, participant.getDiagnosedYear()),
                        MBC.decryptValue(container, receiver, key, participant.getDOBConsent()),
                        MBC.decryptValue(container, receiver, key, participant.getDOBBlood()),
                        participant.getUpdatedAt()));
                MBCInstitution institution = changes.getMbcInstitution();
                DSMServer.putMBCInstitution(institution.getPhysicianId(), new MBCInstitution(institution.getPhysicianId(),
                        MBC.decryptValue(container, receiver, key, institution.getName()),
                        MBC.decryptValue(container, receiver, key, institution.getPhone()),
                        MBC.decryptValue(container, receiver, key, institution.getStreet()),
                        MBC.decryptValue(container, receiver, key, institution.getCity()),
                        MBC.decryptValue(container, receiver, key, institution.getState()),
                        MBC.decryptValue(container, receiver, key, institution.getZip()),
                        MBC.decryptValue(container, receiver, key, institution.getInstitution()),
                        institution.getUpdatedAt(), institution.isFromBloodRelease()));
            }
            logger.info("Finished decryption of participants from MBC");
        }
        return mbcDataChanges;
    }
}
