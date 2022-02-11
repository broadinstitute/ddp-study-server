package org.broadinstitute.dsm.util.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.DDPKitRequest;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.broadinstitute.dsm.util.tools.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

/**
 * Tool to migrate already sent kits into dsm db
 */
public class KitRequestMigrationTool {

    private static final Logger logger = LoggerFactory.getLogger(KitRequestMigrationTool.class);

    private static final String GET_Kit_TYPE_QUERY = "SELECT kit_type_id FROM kit_type WHERE kit_type_name = ?";
    private static final String SET_KIT_RECEIVED_QUERY = "update ddp_kit set receive_date = ? where dsm_kit_request_id = ?";

    private static final String RGP = "RGP";

    private static final String DATSTAT_ALTPID = "DATSTAT_ALTPID";
    private static final String SHORTID = "Short_ID";
    private static final String BSP_PARTICIPANT_ID = "bsp_participant_id";
    private static final String BSP_COLLABORATOR_ID = "bsp_sample_id";
    private static final String SHIPPED = "Shipped Date";
    private static final String TRACKING_TO = "Tracking- to participant";
    private static final String TRACKING_RETURN = "Tracking- retun";

    private static Config cfg;

    private static boolean testScenario = false;

    private static String propFile;
    private static String realmName;
    private static String typeName;
    private static String testJson;
    private static String testFormat;

    private static KitUtil kitUtil;

    public static void main(String[] args) {
        littleMain();
    }

    public static void argumentsForTesting(String propFileTesting, String realm, String type, String json, String format) {
        testScenario = true;
        propFile = propFileTesting;
        realmName = realm;
        typeName = type;
        testJson = json;
        testFormat = format;
    }

    public static void littleMain() {
        try {
            if (!testScenario) {
                String confFile = "config/test-config.conf";
                setup(confFile);

                String realm = "RGP";
                String kitType = "BLOOD";
                String migrationFile = "KitRequestMigrationRGP.txt";
                String fileFormat = "txt";

                migrate(realm, kitType, migrationFile, fileFormat);
            }
            else {
                setup(propFile);
                migrate(realmName, typeName, testJson, testFormat);
            }
        }
        catch (Exception ex) {
            logger.error("Failed to migrate data ", ex);
            System.exit(-1);
        }
    }

    private static void migrate(String realm, String type, String file, @NonNull String fileFormat) {
        try {
            String realmId = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, realm, "ddp_instance_id");
            String collaboratorIdPrefix = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, realm, "collaborator_id_prefix");
            String typeId = DBTestUtil.getQueryDetail(GET_Kit_TYPE_QUERY, type, "kit_type_id");


            HashMap<Integer, KitRequestSettings> carrierServiceTypes = KitRequestSettings.getKitRequestSettings(realmId);
            if (!carrierServiceTypes.isEmpty()) {
                KitRequestSettings kitRequestSettings = carrierServiceTypes.get(Integer.parseInt(typeId));
                if (kitRequestSettings != null) {
                    String bspCollaboratorSampleType = type;
                    if (kitRequestSettings.getCollaboratorSampleTypeOverwrite() != null) {
                        bspCollaboratorSampleType = kitRequestSettings.getCollaboratorSampleTypeOverwrite();
                    }
                    String bspCollaboratorParticipantLength = kitRequestSettings.getCollaboratorParticipantLengthOverwrite();

                    migrateData(file, realmId, typeId, bspCollaboratorSampleType, collaboratorIdPrefix, fileFormat, bspCollaboratorParticipantLength, realm);
                }
                else {
                    logger.error("Failed to get kit request settings for typeID " + typeId + " and realmID "+ realmId);
                }
            }
            else {
                logger.error("Lookup was not populated right!");
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setup(String config) {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File(config)));

        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);

        kitUtil = new KitUtil();
    }

    private static void migrateData(@NonNull String jsonFile, @NonNull String realmId, @NonNull String typeId,
                                    @NonNull String bspCollaboratorSampleType, String collaboratorIdPrefix, @NonNull String fileFormat,
                                    String bspCollaboratorParticipantLength, String realmName) throws Exception {
        // Request KitRequests
        String fileContent = TestUtil.readFile(jsonFile);
        if ("json".equals(fileFormat)) {
            JsonArray scans = (JsonArray) (new JsonParser().parse(fileContent));
            for (JsonElement scan : scans) {
                String ddpParticipantId = scan.getAsJsonObject().get("ddpParticipantId").getAsString();
                String shortId = scan.getAsJsonObject().get("shortId").getAsString();
                String kitlabel = scan.getAsJsonObject().get("kitlabel").getAsString();
                insertIntoDB(cfg.getString(ApplicationConfigConstants.INSERT_KIT_REQUEST), cfg.getString(ApplicationConfigConstants.INSERT_KIT),
                        ddpParticipantId, shortId, realmId, typeId, kitlabel, bspCollaboratorParticipantLength,
                        bspCollaboratorSampleType, collaboratorIdPrefix,
                        null, null, null, null);
            }
        }
        else {
            List<Map<String, String>> content = FileUtil.readFileContent(fileContent);
            for(Map<String, String> line : content) {
                if (RGP.equals(realmName)) {
                    insertIntoDB(cfg.getString(ApplicationConfigConstants.INSERT_KIT_REQUEST), cfg.getString(ApplicationConfigConstants.INSERT_KIT),
                            line.get(DATSTAT_ALTPID), line.get(SHORTID), realmId, typeId, line.get(DBUtil.SM_ID),
                            line.get(BSP_PARTICIPANT_ID), line.get(BSP_PARTICIPANT_ID) , DBUtil.getLong(line.get(SHIPPED)), DBUtil.getLong(line.get(DBUtil.RECEIVED)),
                            line.get(TRACKING_TO), line.get(TRACKING_RETURN));

                }
                else {
                    insertIntoDB(cfg.getString(ApplicationConfigConstants.INSERT_KIT_REQUEST), cfg.getString(ApplicationConfigConstants.INSERT_KIT),
                            line.get(DATSTAT_ALTPID), line.get(DATSTAT_ALTPID), realmId, typeId, line.get(DBUtil.SM_ID),
                            bspCollaboratorParticipantLength,
                            bspCollaboratorSampleType, collaboratorIdPrefix, DBUtil.getLong(line.get(SHIPPED)), DBUtil.getLong(line.get(DBUtil.RECEIVED)),
                            line.get(TRACKING_TO), line.get(TRACKING_RETURN));
                }
            }
        }
    }

    private static void insertIntoDB(String insertKitRequestQuery, String insertKitQuery, String ddpParticipantId, String shortId,
                                     String realm, String typeId, String kitLabel, String bspCollaboratorParticipantLength,
                                     String bspCollaboratorSampleType, String collaboratorIdPrefix,
                                     Long sendDate, Long receivedDate, String trackingTo, String trackingReturn) throws Exception {
        inTransaction((conn) -> {
            try {
                String collaboratorParticipantId = KitRequestShipping.generateBspParticipantID(collaboratorIdPrefix, bspCollaboratorParticipantLength, shortId);
                insertIntoDB(insertKitRequestQuery, insertKitQuery, ddpParticipantId, shortId, realm, typeId, kitLabel,
                        collaboratorParticipantId,
                        KitRequestShipping.generateBspSampleID(conn, collaboratorParticipantId, bspCollaboratorSampleType, Integer.parseInt(typeId)),
                        sendDate, receivedDate,trackingTo, trackingReturn);
            }
            catch (Exception e) {
                throw new RuntimeException(" insertIntoDB ", e);
            }
            return null;
        });
    }

    private static void insertIntoDB(String insertKitRequestQuery, String insertKitQuery, String ddpParticipantId, String shortId,
                String realm, String typeId, String kitLabel, String bspCollaboratorParticipantId, String bspCollaboratorSampleId,
                Long sendDate, Long receivedDate, String trackingTo, String trackingReturn) throws Exception {
        inTransaction((conn) -> {
            try {
                try (PreparedStatement stmt = conn.prepareStatement(insertKitRequestQuery)) {
                    String ddpLabel = KitRequestShipping.generateDdpLabelID();
                    stmt.setString(1, realm);
                    stmt.setString(2, DDPKitRequest.MIGRATED_KIT_REQUEST + KitRequestShipping.createRandom(20));
                    stmt.setString(3, typeId);
                    stmt.setString(4, ddpParticipantId);
                    stmt.setString(5, bspCollaboratorParticipantId);
                    stmt.setString(6, bspCollaboratorSampleId);
                    stmt.setString(7, ddpLabel);
                    stmt.setString(8, "MIGRATION_TOOL");
                    stmt.setLong(9, System.currentTimeMillis());

                    if (stmt.executeUpdate() == 1) {
                        int kitRequestKey = -1;
                        ResultSet rs = stmt.getGeneratedKeys();
                        if (rs.next()) {
                            kitRequestKey = rs.getInt(1);
                        }
                        if (kitRequestKey != -1) {
                            PreparedStatement insertKit = conn.prepareStatement(insertKitQuery);
                            insertKit.setInt(1, kitRequestKey);
                            insertKit.setString(2, null);
                            insertKit.setString(3, null);
                            insertKit.setString(4, null);
                            insertKit.setString(5, null);
                            insertKit.setString(6, trackingTo);
                            insertKit.setString(7, trackingReturn);
                            insertKit.setString(8, null);
                            insertKit.setString(9, null);
                            insertKit.setInt(10, 0);
                            insertKit.setString(11, null);
                            insertKit.setString(12, null);
                            if (insertKit.executeUpdate() == 1) {
                                setToScanned(conn, cfg.getString("portal.setKitToSent"), ddpLabel, kitLabel, sendDate);
                                if (receivedDate != null) {
                                    DBUtil.setToReceived(conn, SET_KIT_RECEIVED_QUERY, kitRequestKey, receivedDate);
                                }
                                logger.info("added kit request w/ id " + kitRequestKey);
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                throw new RuntimeException(" insertIntoDB ", e);
            }
            return null;
        });
    }

    private static void setToScanned(Connection conn, String updateQuery, String ddpLabel, String kitLabel, Long sendDate) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            if (sendDate == null) {
                long currentTime = System.currentTimeMillis();
                stmt.setLong(1, currentTime);
            }
            else {
                stmt.setLong(1, sendDate);
            }
            stmt.setString(2, "");
            stmt.setString(3, kitLabel);
            stmt.setString(4, ddpLabel);
            int rows = stmt.executeUpdate();
            if (rows != 1) {
                throw new RuntimeException("Updated " + rows + " rows");
            }
        }
    }
}
