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
public class MBCHospital {

    private static final Logger logger = LoggerFactory.getLogger(MBCHospital.class);

    public static final String INSTITUTION = "INSTITUTION";
    public static final String INITIAL_BIOPSY = "INITIAL_BIOPSY";

    private String hospitalId;
    private String name;
    private String city;
    private String state;
    private String updatedAt;
    private boolean changedSinceLastChecked;


    public MBCHospital(String hospitalId, String name, String city, String state, String updatedAt) {
        this(hospitalId, name, city, state, updatedAt, false);
    }

    public MBCHospital(String hospitalId, String name, String city, String state, String updatedAt, boolean changedSinceLastChecked) {
        this.hospitalId = hospitalId;
        this.name = name;
        this.city = city;
        this.state = state;
        this.updatedAt = updatedAt;
        this.changedSinceLastChecked = changedSinceLastChecked;
    }

    public static void getAllHospitalInformationFromDB(@NonNull String ddpInstanceName, @NonNull String url,
                                                         @NonNull ScriptingContainer container, @NonNull Object receiver) {
        Map<String, MBCHospital> mbcData = new HashMap<>();
        Connection conn = null;
        try {
            if (Boolean.valueOf(TransactionWrapper.getSqlFromConfig(ddpInstanceName.toLowerCase() + "." + MBC.POSTGRESQL))) {
                Class.forName("org.postgresql.Driver");
                conn = DriverManager.getConnection(url);
                int counter = 0;
                try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_ALL_HOSPITALS_MBC))) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String hospitalId = rs.getString(DBConstants.ID);
                            String participantId = rs.getString(DBConstants.USER_ID);
                            mbcData.put(hospitalId + "_" + participantId, new MBCHospital(hospitalId,
                                    rs.getString(DBConstants.ENCRYPTED_NAME),
                                    rs.getString(DBConstants.ENCRYPTED_CITY),
                                    rs.getString(DBConstants.ENCRYPTED_STATE),
                                    rs.getString(DBConstants.UPDATED_AT)));
                            counter++;
                        }
                        logger.info("Got " + counter + " hospitals from " + ddpInstanceName);
                    }
                }
                catch (SQLException ex) {
                    logger.error("Error getting hospitals from mbc ", ex);
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
        logger.info("Finished getting hospitals from MBC DB");
        //decrypt data
        String key = TransactionWrapper.getSqlFromConfig(ddpInstanceName.toLowerCase() + "." + MBC.ENCRYPTION_KEY);
        for (MBCHospital hospital : mbcData.values()) {
            DSMServer.putMBCHospital(hospital.getHospitalId(), new MBCHospital(hospital.getHospitalId(),
                    MBC.decryptValue(container, receiver, key, hospital.getName()),
                    MBC.decryptValue(container, receiver, key, hospital.getCity()),
                    MBC.decryptValue(container, receiver, key, hospital.getState()),
                    hospital.getUpdatedAt()));
        }
        logger.info("Finished decryption of hospitals from MBC");
    }
}
