package org.broadinstitute.dsm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.ddp.BasicServer;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.Utility;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.*;
import org.broadinstitute.dsm.util.externalShipper.GBFRequestUtil;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Assert;
import org.mockserver.integration.ClientAndServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Spark;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import org.broadinstitute.ddp.BasicServer;

public class TestHelper {

    public static final String SELECT_KITREQUEST_QUERY = "SELECT dsm_kit_request_id FROM ddp_kit_request WHERE ddp_kit_request_id = ? AND ddp_participant_id = ?";
    public static final String SELECT_KIT_QUERY = "SELECT * FROM ddp_kit WHERE dsm_kit_request_id = ?";
    public static final String SELECT_DATA_MEDICALRECORD_QUERY = "SELECT * FROM ddp_medical_record WHERE medical_record_id = ?";

    public static final String DELETE_ASSIGNEE_EMAILS_QUERY = "DELETE FROM EMAIL_QUEUE WHERE EMAIL_RECORD_ID = ?";
    public static final String SELECT_ASSIGNEE_BY_ID_QUERY = "select * from access_user where user_id = ?";
    public static final String SELECT_PARTICIPANT_QUERY = "SELECT * FROM ddp_participant WHERE ddp_participant_id = ?";
    public static final String SELECT_DATA_ONCHISTORY_QUERY = "SELECT * FROM ddp_onc_history WHERE participant_id = ?";
    public static final String SELECT_PARTICIPANTRECORD_QUERY = "SELECT * FROM ddp_participant_record WHERE participant_id = ?";
    public static final String SELECT_EMAILQUEUE_QUERY = "select * from EMAIL_QUEUE where EMAIL_RECORD_ID = ? and REMINDER_TYPE = ? and EMAIL_DATE_PROCESSED is null";
    public static final String SELECT_USER_SETTING = "select * from user_settings settings, access_user user where user.user_id = settings.user_id and user.is_active = 1 and user.name = ?";

    public static final String TEST_DDP = "TestDDP";
    public static final String TEST_DDP_2 = "anotherDDP";
    public static final String TEST_DDP_MIGRATED = "migratedDDP";
    public static final String DDP_PROMISE = "promise";
    public static final String DDP_INSTANCE_ID = "ddp_instance_id";
    public static final String FAKE_DDP_PARTICIPANT_ID = "FAKE_DDP_PARTICIPANT_ID";
    public static final String FAKE_LATEST_KIT = "FAKE_LATEST_KIT";
    public static final String FAKE_DSM_LABEL_UID = "FAKE_DSM_LABEL_UID";
    public static final String FAKE_BILLING_REF = "FAKE_BILLING_REF";
    public static final String FAKE_BSP_TEST = "FAKE_BSP_TEST";
    public static final String CUSTOMS_JSON = "{\"description\": \"T-shirt\", \"quantity\": 1, \"value\": 11, \"weight\": 6, \"origin_country\": \"US\", \"hs_tariff_number\": \"610910\", \"customs_certify\": true,\"customs_signer\": \"Jarrett Streebin\", \"contents_type\": \"gift\", \"contents_explanation\":\"\", \"eel_pfc\": \"NOEEI 30.37(a)\", \"restriction_type\": \"none\", \"restriction_comments\": \"\", \"non_delivery_option\": \"abandon\"}";
    public static final String VERY_LONG_PARTICIPANT_ID = "IAMGROOTIAMGROOTIAMGROOTIAMGROOTIAMGROOTIAMGROOTIAMGROOTVERYLONGPARTICIPANTIDWHICHDOESNOTMAKESENSEBUTWILLTHROWERRORFORCOLLABORATORPARTICIPANTIDANDSAMPLEIDIAMGROOTHOPEFULLYITISNOWLONGENOUGHIAMGROOT";

    public static final String KIT_QUERY_BY_PARTICIPANT = "select * from ddp_kit_request, ddp_kit " +
            "where ddp_kit_request.dsm_kit_request_id = ddp_kit.dsm_kit_request_id " +
            "and ddp_kit_request.ddp_participant_id = ?";
    public static final String KIT_QUERY = "select * from ddp_kit_request, ddp_kit " +
            "where ddp_kit_request.dsm_kit_request_id = ddp_kit.dsm_kit_request_id and " +
            "ddp_instance_id = (select ddp_instance_id from ddp_instance where instance_name = ?) " +
            "and kit_type_id = ? " +
            "and ddp_participant_id = ? order by ddp_kit_request.dsm_kit_request_id desc ";

    private static final Logger logger = LoggerFactory.getLogger(TestHelper.class);

    public static String INSTANCE_ID = null;
    public static String INSTANCE_ID_2 = null;
    public static String INSTANCE_ID_MIGRATED = null;
    public static String PROMISE_INSTANCE_ID = null;
    protected static String DDP_BASE_URL;
    protected static String DSM_BASE_URL;
    protected static RestHighLevelClient esClient;

    public static Config cfg;

    public static DSMServer server;

    public static ClientAndServer mockDDP;

    public static TestUtil testUtil;

    public static DDPKitRequest ddpKitRequest;

    public static KitUtil kitUtil;
    public static DDPRequestUtil ddpRequestUtil;
    public static NotificationUtil notificationUtil;
    public static UserUtil userUtil;
    public static EventUtil eventUtil;

    public static void setupDB() {
        setupDB(false);
    }

    public static void setupDB(boolean setupDDPConfigLookup) {
        cfg = ConfigFactory.load();
//        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File("config/test-config.conf")));
//
//        //overwrite quartz.jobs
        cfg = cfg.withValue("quartz.enableJobs", ConfigValueFactory.fromAnyRef("false"));
        cfg = cfg.withValue("portal.port", ConfigValueFactory.fromAnyRef("9999"));
        cfg = cfg.withValue("errorAlert.recipientAddress", ConfigValueFactory.fromAnyRef(""));
//
        if (!cfg.getString("portal.environment").startsWith("Local")) {
            throw new RuntimeException("Not local environment");
        }

//        if (!cfg.getString("portal.dbUrl").contains("local")) {
//            throw new RuntimeException("Not your test db");
//        }

        if (cfg == null) {
            throw new NullPointerException("config");
        } else {
            logger.info("Setup the DB...");
            boolean skipSsl = false;
            if (cfg.hasPath("portal.dbSkipSsl") && cfg.getBoolean("portal.dbSkipSsl")) {
                logger.warn("DB connection will not use SSL.");
                skipSsl = true;
            }

            int maxConnections = cfg.getInt("portal.maxConnections");
            String dbUrl = cfg.getString("portal.dbUrl");
            if (dbUrl == null) {
                throw new NullPointerException("dbUrl");
            } else {
                logger.info("Skipping DB update...");
            }

            if (!skipSsl) {
                TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
                        cfg.getString("portal.dbSslKeyStorePwd"),
                        cfg.getString("portal.dbSslTrustStore"),
                        cfg.getString("portal.dbSslTrustStorePwd"));
            }

        TransactionWrapper.reset(TestUtil.UNIT_TEST);
            TransactionWrapper.init(maxConnections, dbUrl, cfg, skipSsl);
            if (!Utility.dbCheck()) {
                throw new RuntimeException("DB connection error.");
            } else {
                logger.info("DB setup complete.");
            }
        }
//
//        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
//                cfg.getString("portal.dbSslKeyStorePwd"),
//                cfg.getString("portal.dbSslTrustStore"),
//                cfg.getString("portal.dbSslTrustStorePwd"));
//
//        TransactionWrapper.reset(TestUtil.UNIT_TEST);
//        TransactionWrapper.init(cfg.getInt("portal.maxConnections"), cfg.getString("portal.dbUrl"), cfg, false);
//
//        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 1 where instance_name = \"" + TEST_DDP_MIGRATED + "\"");

        /*
        INSTANCE_ID = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, DDP_INSTANCE_ID);
        INSTANCE_ID_2 = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP_2, DDP_INSTANCE_ID);
        INSTANCE_ID_MIGRATED = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP_MIGRATED, DDP_INSTANCE_ID);
        DDP_BASE_URL = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP, DBConstants.BASE_URL);
        PROMISE_INSTANCE_ID = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, DDP_PROMISE, DDP_INSTANCE_ID);

        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 1 where instance_name = \"" + TEST_DDP + "\"");
        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 1 where instance_name = \"" + TEST_DDP_2 + "\"");
        DBTestUtil.executeQuery("UPDATE ddp_instance set billing_reference = 'CO Test' where instance_name = \"" + TEST_DDP + "\"");
        if (!DBTestUtil.checkIfValueExists("SELECT * from ddp_instance where instance_name = ?", TEST_DDP_MIGRATED)) {
            DBTestUtil.executeQuery("INSERT INTO ddp_instance set instance_name = \"" + TEST_DDP_MIGRATED + "\", base_url=\"https://localhost:6666\", collaborator_id_prefix = \"MigratedProject\", migrated_ddp = 1 ");
            String tmp = DBTestUtil.getQueryDetail(DBUtil.GET_REALM_QUERY, TEST_DDP_MIGRATED, DDP_INSTANCE_ID);
            DBTestUtil.executeQuery("INSERT INTO ddp_kit_request_settings set ddp_instance_id = \"" + tmp + "\", kit_type_id = 1, kit_return_id = 1, carrier_service_to_id = 1, kit_dimension_id = 1 ");
        }
        //add second reminder
        if (!DBTestUtil.checkIfValueExists("SELECT * from event_type where ddp_instance_id = " + INSTANCE_ID + " AND event_name = ?", "BLOOD_SENT_2WK")) {
            DBTestUtil.executeQuery("INSERT INTO event_type set ddp_instance_id = " + INSTANCE_ID + ", event_name=\"BLOOD_SENT_2WK\", event_description=\"Blood kit - reminder email - 2 WKS\", kit_type_id=\"2\", event_type=\"REMINDER\", hours=\"336\"");
        }
        //adding test group
        if (!DBTestUtil.checkIfValueExists("SELECT * from access_user where name = ?", "THE UNIT TESTER 1")) {
            DBTestUtil.executeQuery("INSERT INTO access_user set name = \"THE UNIT TESTER 1\", email=\"simone+1@broadinstitute.org\"");
        }
        //adding test user
        if (!DBTestUtil.checkIfValueExists("SELECT * from ddp_group where name = ?", "test")) {
            DBTestUtil.executeQuery("INSERT INTO ddp_group set name = \"test\", description=\"Unit Test\"");
        }
        //setting roles for test user for test group
        List<String> roles = UserUtil.getUserRolesPerRealm("SELECT role.name FROM  access_user_role_group roleGroup LEFT JOIN ddp_instance_group gr on (gr.ddp_group_id = roleGroup.group_id) " +
                "LEFT JOIN access_user user on (roleGroup.user_id = user.user_id) LEFT JOIN ddp_instance realm on (realm.ddp_instance_id = gr.ddp_instance_id) " +
                "LEFT JOIN access_role role on (role.role_id = roleGroup.role_id) WHERE user.name = ? and instance_name = ?", "THE UNIT TESTER 1", TEST_DDP);
        //setting roles for test user for test group
        List<String> roles2 = UserUtil.getUserRolesPerRealm("SELECT role.name FROM  access_user_role_group roleGroup LEFT JOIN ddp_instance_group gr on (gr.ddp_group_id = roleGroup.group_id) " +
                "LEFT JOIN access_user user on (roleGroup.user_id = user.user_id) LEFT JOIN ddp_instance realm on (realm.ddp_instance_id = gr.ddp_instance_id) " +
                "LEFT JOIN access_role role on (role.role_id = roleGroup.role_id) WHERE user.name = ? and instance_name = ?", "THE UNIT TESTER 1", "angio");
        String testGroup = DBTestUtil.getQueryDetail("SELECT * FROM ddp_group where name = ?", "test", "group_id");
        String testGroup2 = DBTestUtil.getQueryDetail("SELECT * FROM ddp_group where name = ?", "cmi", "group_id");
        String testUser = DBTestUtil.getQueryDetail("SELECT * FROM access_user where name = ?", "THE UNIT TESTER 1", "user_id");

        checkRole("mr_view", roles, testUser, testGroup);
        checkRole("mr_view", roles2, testUser, testGroup2);
        checkRole("mr_request", roles, testUser, testGroup);
        checkRole("ndi_download", roles, testUser, testGroup);
        checkRole("mr_abstraction_admin", roles, testUser, testGroup);
        checkRole("mr_abstracter", roles, testUser, testGroup);
        checkRole("mr_qc", roles, testUser, testGroup);
        checkRole("pdf_download", roles, testUser, testGroup);
        checkRole("participant_event", roles, testUser, testGroup);
        checkRole("kit_deactivation", roles, testUser, testGroup);
        checkRole("discard_sample", roles, testUser, testGroup);
        checkRole("kit_express", roles, testUser, testGroup);
        checkRole("kit_shipping", roles, testUser, testGroup);
        checkRole("kit_shipping_view", roles, testUser, testGroup);
        checkRole("kit_receiving", roles, testUser, testGroup);
        checkRole("kit_upload", roles, testUser, testGroup);
        checkRole("mailingList_view", roles, testUser, testGroup);
        checkRole("participant_event", roles, testUser, testGroup);
        checkRole("participant_exit", roles, testUser, testGroup);
        checkRole("survey_creation", roles, testUser, testGroup);
        checkRole("field_settings", roles, testUser, testGroup);
        checkRole("drug_list_edit", roles, testUser, testGroup);

        //adding instance setting
        if (!DBTestUtil.checkIfValueExists("SELECT * from instance_settings where ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = ?) ", TEST_DDP)) {
            DBTestUtil.executeQuery("INSERT INTO instance_settings set mr_cover_pdf = \"[{\\\"value\\\":\\\"exchange_cb\\\", \\\"name\\\":\\\"MD to MD exchange\\\", \\\"type\\\":\\\"checkbox\\\"}]\", ddp_instance_id = (SELECT ddp_instance_id from ddp_instance where instance_name = \"" + TEST_DDP + "\") ");
        }


        String serverPort = cfg.getString("portal.port");
        DSM_BASE_URL = "http://localhost:" + serverPort;

        if (setupDDPConfigLookup) {
            DSMServer.setupDDPConfigurationLookup(cfg.getString("ddp"));
        }

         */
    }

    private static void checkRole(String role, List<String> roles, String user, String group) {
        if (!roles.contains(role)) {
            DBTestUtil.executeQuery("INSERT INTO access_user_role_group set user_id = " + user + ", group_id = " + group + ", role_id = (SELECT role_id from access_role where name = \"" + role + "\") ");
        }
    }

    public static void addTestParticipant() {
        addTestParticipant(TEST_DDP, "FAKE_DDP_PARTICIPANT_ID", "FAKE_DDP_PHYSICIAN_ID");
    }

    public static void addTestMigratedParticipant() {
        addTestParticipant(TEST_DDP_MIGRATED, "FAKE_MIGRATED_PARTICIPANT_ID", "FAKE_MIGRATED_PHYSICIAN_ID");
    }

    public static void addTestParticipant(@NonNull String realm, @NonNull String fakeDDPParticipantId, @NonNull String fakeDDPInstitutionId) {
        DBTestUtil.createTestData(realm, fakeDDPParticipantId, fakeDDPInstitutionId);
    }

    public static void addTestParticipant(@NonNull String realm, @NonNull String fakeDDPParticipantId, @NonNull String shortId, @NonNull String fakeDDPInstitutionId, @NonNull String lastVersion, boolean addFakeData) {
        DBTestUtil.createTestData(realm, fakeDDPParticipantId, fakeDDPInstitutionId, lastVersion, addFakeData, shortId);
    }

    public static void cleanupDB() {
        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 0 where instance_name = \"" + TEST_DDP + "\"");
        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 0 where instance_name = \"" + TEST_DDP_2 + "\"");
        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 0 where instance_name = \"" + TEST_DDP_MIGRATED + "\"");
    }

    public static void stopDSMServer() {
        Spark.stop();
    }

    public static void startDSMServer() {
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
        server = new DSMServer();
        if (!cfg.getString("portal.environment").startsWith("Local")) {
            throw new RuntimeException("Not local environment");
        }
        server.configureServer(cfg);
    }

    public static void startMockServer() {
        mockDDP = ClientAndServer.startClientAndServer(6666);
    }

    public static void stopMockServer() {
        if (mockDDP != null) {
            mockDDP.stop();
        }
    }

    public static void setupUtils() throws Exception {
        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
                cfg.getString("portal.dbSslKeyStorePwd"),
                cfg.getString("portal.dbSslTrustStore"),
                cfg.getString("portal.dbSslTrustStorePwd"));

        testUtil = TestUtil.newInstance(cfg);
        ddpRequestUtil = new DDPRequestUtil();
        GBFRequestUtil gbfRequestUtil = new GBFRequestUtil();
        kitUtil = new KitUtil();
        ddpKitRequest = new DDPKitRequest();
        notificationUtil = new NotificationUtil(cfg);

        userUtil = new UserUtil();
        eventUtil = new EventUtil();
    }

    public static void setupEsClient() {
        if (esClient == null) {
            if (cfg == null) {
                setupDB();
            }
            try {
                esClient = ElasticSearchUtil.getClientForElasticsearchCloud(cfg.getString("elasticSearch.url"), cfg.getString("elasticSearch.username"), cfg.getString("elasticSearch.password"));
            } catch (MalformedURLException e) {
                throw new RuntimeException("Could not initialize es client",e);
            }
        }

    }

    //Methods shared by more than 2 classes!!!
    public String editMedicalRecord(@NonNull String instanceName, @NonNull String participantId, @NonNull String institutionId, String valueName, String value, String columnName) throws Exception {
        //add value for fax_sent
        List strings = new ArrayList<>();
        strings.add(institutionId);

        String medicalRecordId = DBTestUtil.getQueryDetail(MedicalRecord.SQL_SELECT_MEDICAL_RECORD + " and inst.ddp_institution_id = \"" + institutionId + "\" and p.ddp_participant_id = \"" + participantId + "\"", instanceName, "medical_record_id");

        if (medicalRecordId == null) {
            Assert.fail("medicalRecordId was null!");
        }
        String json = "{\"id\":\"" + medicalRecordId + "\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"" + valueName + "\",\"value\":\"" + value + "\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/" + "patch"), json, testUtil.buildAuthHeaders()).returnResponse();

        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String valueFromDB = DBTestUtil.getQueryDetail(SELECT_DATA_MEDICALRECORD_QUERY, medicalRecordId, columnName);

        if (!valueName.equals("m.followUps")) {
            Assert.assertEquals(value, valueFromDB);
        }

        return medicalRecordId;
    }

    public String addOncHistoryDetails(String participantId) throws Exception {
        //add oncHistoryDetail
        String json = "{\"id\":null,\"parentId\":\"" + participantId + "\",\"parent\":\"participantId\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"oD.datePX\",\"value\":\"2017-01-02\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        JsonObject jsonObject = new JsonParser().parse(message).getAsJsonObject();
        String body = jsonObject.get("body").getAsString();
        jsonObject = new JsonParser().parse(body).getAsJsonObject();
        String oncHistoryId = jsonObject.get("oncHistoryDetailId").getAsString();

        json = "{\"id\":\"" + oncHistoryId + "\",\"parentId\":\"" + participantId + "\",\"parent\":\"participantId\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"oD.typePX\",\"value\":\"typeTesty\"}}";
        response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //check if oncHistoryDetail is in db
        ArrayList strings = new ArrayList<>();
        strings.add(oncHistoryId);
        strings.add("typeTesty");
        //get last changed on with that combination to get the one just added
        Assert.assertEquals("2017-01-02", DBTestUtil.getStringFromQuery("select * from ddp_onc_history_detail where onc_history_detail_id = ? and type_px = ? order by last_changed desc limit 1", strings, "date_px"));

        //check that oncHistoryDetails created was also set
        String created = DBTestUtil.getQueryDetail(SELECT_DATA_ONCHISTORY_QUERY, participantId, "created");
        Assert.assertNotNull(created);
        return oncHistoryId;
    }

    public void changeValue(String valueId, String participantId, String field, Object value, String columnName, String parent, String user, String pkName, String tableName, String fieldId) throws Exception {
        //change value
        String json = "{\"id\":\"" + valueId + "\",\"parentId\":\"" + participantId + "\",\"parent\":\"" + parent + "\",\"user\":\"" + user + "\",\"nameValue\":{\"name\":\"" + field + "\",\"value\":\"" + value + "\"}}";
        if (fieldId != null && valueId != null) {
            json = "{\"id\":\"" + valueId + "\",\"parentId\":\"" + participantId + "\",\"parent\":\"" + parent + "\",\"user\":\"" + user + "\",\"fieldId\":\"" + fieldId + "\",\"nameValue\":{\"name\":\"" + field + "\",\"value\":\"" + value + "\"}}";
        }
        else if (fieldId != null && valueId == null) {
            if (value instanceof Integer) {
                json = "{\"id\":null,\"parentId\":\"" + participantId + "\",\"parent\":\"" + parent + "\",\"user\":\"" + user + "\",\"fieldId\":\"" + fieldId + "\",\"nameValue\":{\"name\":\"" + field + "\",\"value\":" + value + "}}";
            }
            else {
                json = "{\"id\":null,\"parentId\":\"" + participantId + "\",\"parent\":\"" + parent + "\",\"user\":\"" + user + "\",\"fieldId\":\"" + fieldId + "\",\"nameValue\":{\"name\":\"" + field + "\",\"value\":\"" + value + "\"}}";
            }
        }
        if (String.valueOf(value).startsWith("[")) {//for abstraction value change
            json = "{\"id\":null,\"parentId\":\"" + participantId + "\",\"parent\":\"" + parent + "\",\"user\":\"" + user + "\",\"fieldId\":\"" + fieldId + "\",\"nameValue\":{\"name\":\"" + field + "\",\"value\":" + value + "}}";
        }
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //check changed value
        if (fieldId == null && value instanceof String) {
            ArrayList strings = new ArrayList<>();
            strings.add(valueId);
            if (StringUtils.isNotBlank((String) value)) {
                Assert.assertEquals(value, DBTestUtil.getStringFromQuery("select * from " + tableName + " where " + pkName + " = ?", strings, columnName));
            }
            else {
                Assert.assertNull(DBTestUtil.getStringFromQuery("select * from " + tableName + " where " + pkName + " = ?", strings, columnName));
            }
        }
    }

    public void changeOncHistoryValue(String oncHistoryId, String participantId, String field, String value, String columnName) throws Exception {
        changeValue(oncHistoryId, participantId, field, value, columnName, "participantId", "ptaheri+1@broadinstitute.org", "onc_history_detail_id", "ddp_onc_history_detail", null);
    }

    public static String addTissue(String oncHistoryId) throws Exception {
        //add oncHistoryDetail
        String note = "Created: " + System.currentTimeMillis();
        String json = "{\"id\":null,\"parentId\":\"" + oncHistoryId + "\",\"parent\":\"oncHistoryDetailId\",\"user\":\"simone+1@broadinstitute.org\",\"nameValue\":{\"name\":\"t.tNotes\",\"value\":\"" + note + "\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String message = DDPRequestUtil.getContentAsString(response);
        JsonObject jsonObject = new JsonParser().parse(message).getAsJsonObject();
        String body = jsonObject.get("body").getAsString();
        jsonObject = new JsonParser().parse(body).getAsJsonObject();
        String tissueId = jsonObject.get("tissueId").getAsString();

        //check if tissue is in db
        ArrayList strings = new ArrayList<>();
        strings.add(tissueId);
        strings.add(note);
        //get last changed on with that combination to get the one just added
        Assert.assertEquals(note, DBTestUtil.getStringFromQuery("select * from ddp_tissue where tissue_id = ? and notes = ? order by last_changed desc limit 1", strings, "notes"));
        return tissueId;
    }

    public static void changeTissueValue(String tissueId, String oncHistoryId, String field, String value, String columnName) throws Exception {
        //change value of oncHistoryDetail
        String json = "{\"id\":\"" + tissueId + "\",\"parentId\":\"" + oncHistoryId + "\",\"parent\":\"oncHistoryDetailId\",\"user\":\"ptaheri+1@broadinstitute.org\",\"nameValue\":{\"name\":\"" + field + "\",\"value\":\"" + value + "\"}}";
        HttpResponse response = TestUtil.perform(Request.Patch(DSM_BASE_URL + "/ui/patch"), json, testUtil.buildAuthHeaders()).returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());

        //check changed value
        ArrayList strings = new ArrayList<>();
        strings.add(tissueId);
        if (StringUtils.isNotBlank(value)) {
            Assert.assertEquals(value, DBTestUtil.getStringFromQuery("select * from ddp_tissue where tissue_id = ?", strings, columnName));
        }
        else {
            Assert.assertNull(DBTestUtil.getStringFromQuery("select * from ddp_tissue where tissue_id = ?", strings, columnName));
        }
    }

    public static String randomStringGenerator(int length, boolean includeLetters, boolean includeSpace, boolean includeNumbers) {
        String AlphaNumericString = "";

        if (includeLetters) {
            AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                    + "abcdefghijklmnopqrstuvxyz";
        }
        if (includeSpace) {
            AlphaNumericString += " ";
        }

        if (includeNumbers) {
            AlphaNumericString += "0123456789";
        }

        // create StringBuffer size of output
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = (int) (AlphaNumericString.length() * Math.random());
            sb.append(AlphaNumericString.charAt(index));
        }

        return sb.toString();
    }
}
