package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_ADMIN_TEST_USER_AUTH0_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_CLIENT_NAME;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_CLIENT_SECRET;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_USER_AUTH0_ID;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.broadinstitute.ddp.DataDonationPlatform;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.filter.DsmAuthFilterTest;
import org.broadinstitute.ddp.filter.StudyLanguageContentLanguageSettingFilterTest;
import org.broadinstitute.ddp.filter.UserAuthCheckFilterTest;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.broadinstitute.ddp.security.JWTConverterTest;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.DBTestContainer;
import org.broadinstitute.ddp.util.LogbackConfigurationPrinter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.reflections.Reflections;

/**
 * Test suite that runs route integration tests. Boots the app in a separate thread or process
 * before running the tests, if configured to do so. To register a test class with test suite,
 * extend the `TestCase` base class and add the class to the `SuiteClasses` annotation.
 */
@Slf4j
@RunWith(Suite.class)
@SuiteClasses({
        GetActivityInstanceListForActivityInstanceSelectQuestionRouteStandaloneTest.class,
        AuthFilterRouteTest.class,
        EventServiceTest.class,
        InvitationCheckStatusRouteTest.class,
        CreateActivityInstanceRouteTest.class,
        CreateTemporaryUserRouteTest.class,
        CreateUserActivityUploadRouteTest.class,
        DeleteMedicalProviderRouteTest.class,
        DsmTriggerOnDemandActivityRouteTest.class,
        EmbeddedComponentActivityInstanceTest.class,
        ErrorHandlingRouteTest.class,
        GetActivityInstanceSummaryRouteTest.class,
        GetConsentSummariesRouteTest.class,
        GetConsentSummaryRouteTest.class,
        GetDsmKitRequestsRouteTest.class,
        GetDsmOnDemandActivitiesRouteTest.class,
        GetDsmReleasePdfRouteTest.class,
        GetDsmTriggeredInstancesRouteTest.class,
        GetDsmMedicalRecordRouteTest.class,
        GetGovernedStudyParticipantsRouteTest.class,
        AdminLookupInvitationRouteTest.class,
        GetInstitutionSuggestionsRouteTest.class,
        GetMailAddressInfoRouteTest.class,
        GetMedicalProviderListRouteTest.class,
        GetPrequalifierInstanceRouteTest.class,
        GetUserAnnouncementsRouteTest.class,
        GetWorkflowRouteTest.class,
        HealthCheckRouteTest.class,
        JWTConverterTest.class,
        MailAddressRouteTest.class,
        PatchMedicalProviderRouteTest.class,
        PostMedicalProviderRouteTest.class,
        ProfileRouteTest.class,
        JoinMailingListRouteTest.class,
        AdminUpdateInvitationDetailsRouteTest.class,
        UserAuthCheckFilterTest.class,
        DsmAuthFilterTest.class,
        GetDsmMailingListRouteTest.class,
        CheckIrbPasswordRouteTest.class,
        ReceiveDsmNotificationRouteTest.class,
        GetDsmInstitutionRequestsRouteTest.class,
        DsmExitUserRouteTest.class,
        SendExitNotificationRouteTest.class,
        GetDsmParticipantInstitutionsRouteTest.class,
        PostPasswordResetRouteTest.class,
        GetDsmConsentPdfRouteTest.class,
        GetPdfRouteTest.class,
        GetDsmStudyParticipantTest.class,
        GetDsmParticipantStatusRouteTest.class,
        ListStudyLanguagesRouteTest.class,
        ListUserStudyInvitationsRouteTest.class,
        GetStudyPasswordPolicyRouteTest.class,
        UpdateUserPasswordRouteTest.class,
        UpdateUserEmailRouteTest.class,
        GetStudiesRouteTest.class,
        InvitationVerifyRouteTest.class,
        StudyLanguageContentLanguageSettingFilterTest.class,
        GetStudyDetailRouteTest.class,
        GetStudyStatisticsRouteTest.class,
        DeleteUserRouteTest.class
})
public class IntegrationTestSuite {
    private static final String DEBUG_FLAG = "-agentlib:jdwp";
    private static int callCounter = 0;

    @BeforeClass
    public static void setup() {
        setup(true);
    }

    public static void setup(boolean isCachingDisabled) {
        SuiteClasses annotation = IntegrationTestSuite.class.getDeclaredAnnotation(SuiteClasses.class);

        List<Class> declaredClasses = Arrays.asList(annotation.value());

        Reflections reflections = new Reflections("org.broadinstitute.ddp.route");
        Set<Class<? extends TestCase>> coveredClasses = reflections.getSubTypesOf(IntegrationTestSuite.TestCase.class);

        List missingClasses = coveredClasses.stream()
                .map(Object.class::cast)
                .filter(coveredClass -> !ignoredClasses().contains(coveredClass))
                .filter(coveredClass -> !declaredClasses.contains(coveredClass))
                .collect(Collectors.toList());

        log.warn("There are some classes missing from suite: " + missingClasses.toString());

        //        if (!missingClasses.isEmpty()) {
        //            fail("There were classes that inherit from TestCase that are not being run in our Suite "
        //            + missingClasses.toString());
        //        }

        LogbackConfigurationPrinter.printLoggingConfiguration();

        initializeDatabase();
        if (!isTestServerRunning()) {
            startupTestServer(isCachingDisabled);
        }
        insertTestData();
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);

        callCounter += 1;
    }

    private static void initializeDatabase() {
        RouteTestUtil.loadConfig();
        RouteTestUtil.loadSqlConfig();
        DBTestContainer.initializeTestDbs();
    }

    public static void startupTestServer(boolean isCacheDisabled) {
        bootAppServer(isCacheDisabled);
        waitForServer(1000);
    }

    private static void insertTestData() {
        RouteTestUtil.setupDatabasePool();
        TestDataSetupUtil.insertStaticTestData();
        initializeStaticTestUserData();
    }

    private static List<Class> ignoredClasses() {
        List<Class> ignoredClassList = new ArrayList<>();
        ignoredClassList.add(DsmRouteTest.class);
        return ignoredClassList;
    }

    /**
     * Inserts static test user data for test users (both regular and admin) in
     * a cross-environment compatible manner so that the tests users get the appropriate
     * auth0 user id
     */
    private static void initializeStaticTestUserData() {
        Config auth0Config = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0);
        String testClientName = auth0Config.getString(AUTH0_CLIENT_NAME);
        String testUserAuth0UserId = auth0Config.getString(AUTH0_TEST_USER_AUTH0_ID);
        String adminTestUserAuth0Id = auth0Config.getString(AUTH0_ADMIN_TEST_USER_AUTH0_ID);
        String testClientId = auth0Config.getString(AUTH0_CLIENT_ID);
        String testClientSecret = auth0Config.getString(AUTH0_CLIENT_SECRET);
        String defaultDomain = auth0Config.getString(ConfigFile.DOMAIN);
        String encryptionSecret = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);
        String mgmtApiClientId = auth0Config.getString(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID);
        String mgmtApiClientSecret = auth0Config.getString(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET);


        TransactionWrapper.useTxn(handle -> {
            JdbiClient jdbiClient = handle.attach(JdbiClient.class);
            JdbiUmbrellaStudy jdbiStudy = handle.attach(JdbiUmbrellaStudy.class);
            JdbiAuth0Tenant jdbiAuth0Tenant = handle.attach(JdbiAuth0Tenant.class);
            String encryptedSecret = AesUtil.encrypt(mgmtApiClientSecret, EncryptionKey.getEncryptionKey());
            Auth0TenantDto auth0Tenant = jdbiAuth0Tenant.insertIfNotExists(defaultDomain,
                    mgmtApiClientId,
                    encryptedSecret);

            Optional<Long> clientId = jdbiClient.getClientIdByAuth0ClientAndDomain(testClientId, defaultDomain);

            if (!clientId.isPresent()) {
                // add the client and access to the test studies if needed
                long insertedClientId = handle.attach(ClientDao.class)
                        .registerClient(testClientId, testClientSecret, new ArrayList<>(),
                                encryptionSecret, auth0Tenant.getId());
                List<String> testStudies = new ArrayList<>();
                testStudies.add(TestConstants.TEST_STUDY_GUID);

                for (String studyName : testStudies) {
                    StudyDto studyDto = jdbiStudy.findByStudyGuid(studyName);
                    JdbiClientUmbrellaStudy jdbiClientUmbrellaStudy = handle.attach(JdbiClientUmbrellaStudy.class);
                    jdbiClientUmbrellaStudy.insert(insertedClientId, studyDto.getId());
                }
                log.info("Inserted test client {}", insertedClientId);
            }

            // set the test user's auth0Id according to whatever is in our environment, since
            // different environments have different tenants and different users in different tenants
            // can have different auth0 user ids
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);

            Map<String, String> guidToAuth0UserIds = new HashMap<>();
            guidToAuth0UserIds.put(TestConstants.TEST_USER_GUID, testUserAuth0UserId);
            guidToAuth0UserIds.put(TestConstants.TEST_ADMIN_GUID, adminTestUserAuth0Id);
            for (Map.Entry<String, String> guidAndAuth0IdTuple : guidToAuth0UserIds.entrySet()) {
                String testUserGuid = guidAndAuth0IdTuple.getKey();
                String testUserAuth0Id = guidAndAuth0IdTuple.getValue();
                UserDto testUserDto = jdbiUser.findByUserGuid(testUserGuid);
                if (testUserDto == null) {
                    log.warn("Could not find test user {}", testUserGuid);
                }
                int numRowsUpdated = jdbiUser.updateAuth0Id(testUserGuid, testUserAuth0Id);
                if (numRowsUpdated != 1) {
                    log.error("Updated {} rows to for test user {}", numRowsUpdated, testUserGuid);
                }
            }
        });
    }

    @AfterClass
    public static void tearDown() {
        callCounter -= 1;
        if (callCounter > 0) {
            // There are still tests running, do nothing and exit.
            return;
        }
        tearDownSuiteServer();

    }

    public static void tearDownSuiteServer() {
        DataDonationPlatform.shutdown();
        TransactionWrapper.reset();
    }

    private static void bootAppServer(boolean isCacheDisabled) {
        Config cfg = RouteTestUtil.getConfig();
        int port = cfg.getInt(ConfigFile.PORT);
        Map<String, String> serverArgs = new HashMap<>();
        serverArgs.put("config.file", RouteTestUtil.getConfigFilePath());
        serverArgs.put("cachingDisabled", isCacheDisabled + "");

        log.info("Starting app on port {}", port);

        runDdpServer(isCacheDisabled);
    }

    private static void runDdpServer(boolean isCachingDisabled) {
        long startTime = System.currentTimeMillis();
        log.info("Booting ddp...");
        System.setProperty("cachingDisabled", isCachingDisabled + "");
        try {
            DataDonationPlatform.start();
        } catch (MalformedURLException e) {
            log.error("Could not start server", e);
            Assert.fail("Could not start server");
        }
    }

    private static void waitForServer(int millisecs) {
        try {
            log.info("Pausing for {}ms for server to stabilize", millisecs);
            Thread.sleep(millisecs);
        } catch (InterruptedException e) {
            log.info("Wait interrupted", e);
        }
    }

    /**
     * Base class that provides the hooks back into the test suite in order to allow a single test
     * class or test method to run within an initialized environment for route testing.
     */
    public abstract static class TestCase {
        @BeforeClass
        public static void doSuiteSetup() {
            IntegrationTestSuite.setup(true);
        }

        @AfterClass
        public static void doSuiteTearDown() {
            IntegrationTestSuite.tearDown();
        }
    }

    /**
     * Base class that provides the hooks back into the test suite in order to allow a single test
     * class or test method to run within an initialized environment for route testing.
     */
    public abstract static class TestCaseWithCacheEnabled {
        @BeforeClass
        public static void doSuiteSetup() {
            IntegrationTestSuite.setup(false);
        }

        @AfterClass
        public static void doSuiteTearDown() {
            IntegrationTestSuite.tearDown();
        }
    }

    private static boolean isTestServerRunning() {
        int serverPortNum = ConfigManager.getInstance().getConfig().getInt(ConfigFile.PORT);
        String serverUrl = "http://localhost:" + serverPortNum;
        try (
                CloseableHttpClient httpClient = HttpClients.createDefault();

        ) {
            httpClient.execute(new HttpGet(serverUrl));
            log.info("Test server is running already");
            return true;
        } catch (HttpHostConnectException e) {
            log.info("Could not connect to server url. Must not be running");
            return false;
        } catch (IOException e) {
            String msg = "There was problem initializing CloseableHttpClient";
            log.error(msg, e);
            throw new DDPException(msg, e);
        }

    }

    /**
     * Called by circleci when creating a dss backend
     * to run tests against.  Starting a test instance
     * from scratch can take 30s or more, during which
     * time most routes will not work properly.  To check
     * for readiness, ping port 5999 with a utility
     * like netcat.  When you can connect to port 5999,
     * the instance is ready for traffic.  Note that
     * this administrative port is only available for one
     * connection.  After that, the port closes.
     */
    public static void main(String[] args) {
        initializeDatabase();
        startupTestServer(true);
        insertTestData();
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
        // now start a separate service that circleCI can ping once
        // all the test data has been loaded and client tests can run
        new SingleUseServerSocket(5999);
    }

    /**
     * Server socket that allows one connection
     * and then closes
     */
    public static class SingleUseServerSocket {

        public SingleUseServerSocket(int port) {
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try (Socket clientSocket = serverSocket.accept()) {
                log.info("CI/CD readiness port connected, now closing");
                serverSocket.close();
            } catch (IOException e) {
                log.error("socket error", e);
            }
        }
    }

}
