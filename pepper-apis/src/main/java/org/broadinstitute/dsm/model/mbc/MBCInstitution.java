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
public class MBCInstitution {

    private static final Logger logger = LoggerFactory.getLogger(MBCInstitution.class);

    public static final String PHYSICIAN = "PHYSICIAN";

    private String physicianId;
    private String name;
    private String phone;
    private String street;
    private String city;
    private String state;
    private String zip;
    private String institution;
    private String updatedAt;
    private boolean isFromBloodRelease;
    private boolean changedSinceLastChecked;


    public MBCInstitution(String physicianId, String name, String phone, String street, String city, String state,
                          String zip, String institution, String updatedAt, boolean isFromBloodRelease) {
        this(physicianId, name, phone, street, city, state, zip, institution, updatedAt, isFromBloodRelease, false);
    }

    public MBCInstitution(String physicianId, String name, String phone, String street, String city, String state,
                          String zip, String institution, String updatedAt, boolean isFromBloodRelease, boolean changedSinceLastChecked) {
        this.physicianId = physicianId;
        this.name = name;
        this.phone = phone;
        this.street = street;
        this.city = city;
        this.state = state;
        this.zip = zip;
        this.institution = institution;
        this.updatedAt = updatedAt;
        this.isFromBloodRelease = isFromBloodRelease;
        this.changedSinceLastChecked = changedSinceLastChecked;
    }

    public static void getAllPhysiciansInformationFromDB(@NonNull String ddpInstanceName, @NonNull String url,
                                                         @NonNull ScriptingContainer container, @NonNull Object receiver) {
        Map<String, MBCInstitution> mbcData = new HashMap<>();
        Connection conn = null;
        try {
            if (Boolean.valueOf(TransactionWrapper.getSqlFromConfig(ddpInstanceName.toLowerCase() + "." + MBC.POSTGRESQL))) {
                Class.forName("org.postgresql.Driver");
                conn = DriverManager.getConnection(url);
                int counter = 0;
                try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GET_ALL_PHYSICIAN_MBC))) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String physicianId = rs.getString(DBConstants.ID);
                            String participantId = rs.getString(DBConstants.USER_ID);
                            mbcData.put(physicianId + "_" + participantId, new MBCInstitution(physicianId,
                                    rs.getString(DBConstants.ENCRYPTED_NAME),
                                    rs.getString(DBConstants.ENCRYPTED_PHONE),
                                    rs.getString(DBConstants.ENCRYPTED_STREET),
                                    rs.getString(DBConstants.ENCRYPTED_CITY),
                                    rs.getString(DBConstants.ENCRYPTED_STATE),
                                    rs.getString(DBConstants.ENCRYPTED_ZIP),
                                    rs.getString(DBConstants.ENCRYPTED_INSTITUTION),
                                    rs.getString(DBConstants.UPDATED_AT),
                                    rs.getBoolean(DBConstants.IS_BLOOD_RELEASE)));
                            counter++;
                        }
                        logger.info("Got " + counter + " physicians from " + ddpInstanceName);
                    }
                }
                catch (SQLException ex) {
                    logger.error("Error getting institutions from mbc ", ex);
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
        logger.info("Finished getting physicians from MBC DB");
        //decrypt data
        String key = TransactionWrapper.getSqlFromConfig(ddpInstanceName.toLowerCase() + "." + MBC.ENCRYPTION_KEY);
        for (MBCInstitution institution : mbcData.values()) {
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
        logger.info("Finished decryption of physicians from MBC");
    }
}
