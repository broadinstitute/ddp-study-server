package org.broadinstitute.dsm.util.tools;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.ParticipantEvent;
import org.broadinstitute.dsm.model.KitDDPNotification;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/*Tool to stop reminder emails from going out after migration */
public class EventQueueTool {

    private static final Logger logger = LoggerFactory.getLogger(EventQueueTool.class);

    private static Config cfg;

    private static boolean testScenario = false;

    private static String propFile;
    private static String realm;
    private static String eventType;

    public static void main(String[] args) {
        littleMain();
    }

    public static void argumentsForTesting(@NonNull String propFileTesting, @NonNull String realmTesting, @NonNull String eventTypeTesting) {
        testScenario = true;
        propFile = propFileTesting;
        realm = realmTesting;
        eventType = eventTypeTesting;
    }

    public static void littleMain() {
        try {

            if (!testScenario) {
                String confFile = "config/test-config.conf";
                setup(confFile);

                String realm = "MBC";
                String eventType = "BLOOD_SENT_4WK";

                TransactionWrapper.inTransaction(conn -> {
                    eventQueue(conn, realm, eventType);
                    return null;
                });
            }
            else {
                setup(propFile);
                TransactionWrapper.inTransaction(conn -> {
                    eventQueue(conn, realm, eventType);
                    return null;
                });
            }
        }
        catch (Exception ex) {
            logger.error("Failed to migrate data ", ex);
            System.exit(-1);
        }
    }

    private static void setup(String config) {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File(config)));

        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
                cfg.getString("portal.dbSslKeyStorePwd"),
                cfg.getString("portal.dbSslTrustStore"),
                cfg.getString("portal.dbSslTrustStorePwd"));

        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);
    }

    private static void eventQueue(Connection conn, @NonNull String realm, @NonNull String eventName) {
        //find kits which need to get added to EVENT_QUEUE
        Collection<KitDDPNotification> kitsNotReceived = getKitsNotReceived(realm, eventName);

        long currentTime = System.currentTimeMillis();
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);

        for (KitDDPNotification kitInfo : kitsNotReceived) {
            if (kitInfo.getEventName().equals(eventName) && kitInfo.getInstanceName().equals(realm)) {
                //add kits to ddp_participant_event
                ParticipantEvent.skipParticipantEvent(kitInfo.getParticipantId(), currentTime, "SYSTEM", ddpInstance, eventName);
                EventUtil.addEvent(conn, eventName, ddpInstance.getDdpInstanceId(), kitInfo.getDsmKitRequestId(), false);
            }
        }
    }

    private static Collection<KitDDPNotification> getKitsNotReceived(@NonNull String realmName, @NonNull String skipEventName) {
        ArrayList<KitDDPNotification> kitDDPNotifications = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(EventUtil.SQL_SELECT_KIT_FOR_REMINDER_EMAILS + " AND realm.instance_name = ?")) {
                stmt.setString(1, DBConstants.KIT_PARTICIPANT_NOTIFICATIONS_ACTIVATED);
                stmt.setString(2, realmName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String participantId = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        String instanceName = rs.getString(DBConstants.INSTANCE_NAME);
                        String eventName = rs.getString(DBConstants.EVENT_NAME);
                        if (StringUtils.isNotBlank(instanceName) && instanceName.equals(realmName) &&
                                StringUtils.isNotBlank(eventName) && eventName.equals(skipEventName)) {
                            kitDDPNotifications.add(new KitDDPNotification(participantId,
                                    rs.getString(DBConstants.DSM_KIT_REQUEST_ID), rs.getString(DBConstants.DDP_INSTANCE_ID), realmName,
                                    rs.getString(DBConstants.BASE_URL), eventName,
                                    rs.getString(DBConstants.EVENT_TYPE), System.currentTimeMillis(),
                                    rs.getBoolean(DBConstants.NEEDS_AUTH0_TOKEN),
                                    rs.getString(DBConstants.UPLOAD_REASON),
                                    rs.getString(DBConstants.DDP_KIT_REQUEST_ID)));
                        }
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Error getting list of kit requests which aren't received yet (for reminder emails) ", results.resultException);
        }
        logger.info("Found " + kitDDPNotifications.size() + " kit requests for which the ddp needs to trigger reminder emails.");
        return kitDDPNotifications;
    }
}
