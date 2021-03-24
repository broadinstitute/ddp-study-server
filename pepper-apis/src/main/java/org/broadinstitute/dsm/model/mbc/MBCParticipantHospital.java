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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class MBCParticipantHospital {

    private static final Logger logger = LoggerFactory.getLogger(MBCParticipantHospital.class);

    private final int participantId;
    private final int hospitalId;
    private final MBCParticipant mbcParticipant;
    private final MBCHospital mbcHospital;

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public MBCParticipantHospital(int participantId, int hospitalId, MBCParticipant mbcParticipant, MBCHospital mbcHospital) {
        this.participantId = participantId;
        this.hospitalId = hospitalId;
        this.mbcParticipant = mbcParticipant;
        this.mbcHospital = mbcHospital;
    }

    public static Map<String, MBCParticipantHospital> getHospitalsFromDB(@NonNull String ddpInstanceName, @NonNull String url, long value, long timeLastCheck,
                                                                         @NonNull ScriptingContainer container, @NonNull Object receiver, boolean alreadyAddedOnServerStart) {
        Map<String, MBCParticipantHospital> mbcDataChanges = new HashMap<>();
        Connection conn = null;
        try {
            if (Boolean.valueOf(TransactionWrapper.getSqlFromConfig(ddpInstanceName + "." + MBC.POSTGRESQL))) {
                Class.forName("org.postgresql.Driver");
                conn = DriverManager.getConnection(url);
                int counter = 0;
                try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_NEW_HOSPITALS_MBC))) {
                    stmt.setLong(1, value);
                    stmt.setLong(2, timeLastCheck);
                    stmt.setLong(3, timeLastCheck);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String participantId = rs.getString(DBConstants.USER_ID);
                            String hospitalId = rs.getString(DBConstants.ID);
                            String institutionLastChanged = rs.getString(DBConstants.HP_UPDATED_AT);
                            Long updatedAt = null;
                            try {
                                Date date = sdf.parse(institutionLastChanged);
                                updatedAt = date.getTime();
                            }
                            catch (ParseException e) {
                                logger.warn("Couldn't parse hospital update_at to date");
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
                            MBCHospital hospital = new MBCHospital(hospitalId,
                                    rs.getString(DBConstants.ENCRYPTED_NAME),
                                    rs.getString(DBConstants.ENCRYPTED_CITY),
                                    rs.getString(DBConstants.ENCRYPTED_STATE),
                                    institutionLastChanged,
                                    updatedAt != null && timeLastCheck < updatedAt ? true : false);
                            mbcDataChanges.put(hospitalId, new MBCParticipantHospital(Integer.parseInt(participantId), Integer.parseInt(hospitalId), participant, hospital));
                            counter++;
                        }
                        logger.info("Got " + counter + " new/updated hospitals from " + ddpInstanceName + " bookmark was " + value + " lastTimeChecked " + timeLastCheck);
                    }
                }
                catch (SQLException ex) {
                    logger.error("Error getting hospitals from mbc ", ex);
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
            logger.error("SQL exception while trying to get hospitals from postgresql db ", e);
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
            for (MBCParticipantHospital changes : mbcDataChanges.values()) {
                MBCHospital hospital = changes.getMbcHospital();
                DSMServer.putMBCHospital(hospital.getHospitalId(), new MBCHospital(hospital.getHospitalId(),
                        MBC.decryptValue(container, receiver, key, hospital.getName()),
                        MBC.decryptValue(container, receiver, key, hospital.getCity()),
                        MBC.decryptValue(container, receiver, key, hospital.getState()),
                        hospital.getUpdatedAt()));
            }
            logger.info("Finished decryption of participants from MBC");
        }
        return mbcDataChanges;
    }
}
