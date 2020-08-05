package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_ADMIN_TEST_USER_AUTH0_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_CLIENT_NAME;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_CLIENT_SECRET;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_USER_AUTH0_ID;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
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
import org.broadinstitute.ddp.util.JavaProcessSpawner;
import org.broadinstitute.ddp.util.LogbackConfigurationPrinter;
import org.broadinstitute.ddp.util.MySqlTestContainerUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test suite that runs route integration tests. Boots the app in a separate thread or process
 * before running the tests, if configured to do so. To register a test class with test suite,
 * extend the `TestCase` base class and add the class to the `SuiteClasses` annotation.
 */
@RunWith(Suite.class)
@SuiteClasses({
        AdminCreateStudyParticipantRouteTest.class,
        AuthFilterRouteTest.class,
        EventServiceTest.class,
        InvitationCheckStatusRouteTest.class,
        CreateActivityInstanceRouteTest.class,
        CreateTemporaryUserRouteTest.class,
        DeleteMedicalProviderRouteTest.class,
        DsmTriggerOnDemandActivityRouteTest.class,
        EmbeddedComponentActivityInstanceTest.class,
        ErrorHandlingRouteTest.class,
        GetActivityInstanceRouteTest.class,
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
        PatchFormAnswersRouteTest.class,
        PatchMedicalProviderRouteTest.class,
        PostMedicalProviderRouteTest.class,
        ProfileRouteTest.class,
        PutFormAnswersRouteTest.class,
        JoinMailingListRouteTest.class,
        AdminUpdateInvitationDetailsRouteTest.class,
        UserActivityInstanceListRouteTest.class,
        UserAuthCheckFilterTest.class,
        UserRegistrationRouteTest.class,
        DsmAuthFilterTest.class,
        GetDsmMailingListRouteTest.class,
        CheckIrbPasswordRouteTest.class,
        ReceiveDsmNotificationRouteTest.class,
        GetDsmInstitutionRequestsRouteTest.class,
        DsmExitUserRouteTest.class,
        SendEmailRouteTest.class,
        SendExitNotificationRouteTest.class,
        GetDsmParticipantInstitutionsRouteTest.class,
        PostPasswordResetRouteTest.class,
        GetDsmConsentPdfRouteTest.class,
        GetPdfRouteTest.class,
        GetDsmStudyParticipantTest.class,
        GetDsmParticipantStatusRouteTest.class,
        GetCancerSuggestionsRouteTest.class,
        GetDsmDrugSuggestionsRouteTest.class,
        GetParticipantInfoRouteTest.class,
        ListCancersRouteTest.class,
        ListStudyLanguagesRouteTest.class,
        ListUserStudyInvitationsRouteTest.class,
        GetStudyPasswordPolicyRouteTest.class,
        UpdateUserPasswordRouteTest.class,
        UpdateUserEmailRouteTest.class,
        GetStudiesRouteTest.class,
        InvitationVerifyRouteTest.class,
        StudyLanguageContentLanguageSettingFilterTest.class,
        GetStudyDetailRouteTest.class
})
public class IntegrationTestSuite {

    private static final String DEBUG_FLAG = "-agentlib:jdwp";
    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestSuite.class);
    private static int callCounter = 0;

    @BeforeClass
    public static void setup() {
        if (callCounter > 0) {
            // Test suite has been setup already, increment and exit.
            callCounter += 1;
            return;
        }
        SuiteClasses annotation = IntegrationTestSuite.class.getDeclaredAnnotation(SuiteClasses.class);

        List<Class> declaredClasses = Arrays.asList(annotation.value());

        Reflections reflections = new Reflections("org.broadinstitute.ddp.route");
        Set<Class<? extends TestCase>> coveredClasses = reflections.getSubTypesOf(IntegrationTestSuite.TestCase.class);

        List missingClasses = coveredClasses.stream()
                .map(Object.class::cast)
                .filter(coveredClass -> !ignoredClasses().contains(coveredClass))
                .filter(coveredClass -> !declaredClasses.contains(coveredClass))
                .collect(Collectors.toList());

        if (!missingClasses.isEmpty()) {
            fail("There were classes that inherit from TestCase that are not being run in our Suite " + missingClasses.toString());
        }

        LogbackConfigurationPrinter.printLoggingConfiguration();

        initializeDatabase();
        if (!isTestServerRunning()) {
            startupTestServer();
        }
        insertTestData();
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);

        callCounter += 1;
    }

    private static void initializeDatabase() {
        RouteTestUtil.loadConfig();
        RouteTestUtil.loadSqlConfig();
        MySqlTestContainerUtil.initializeTestDbs();
    }

    public static void startupTestServer() {
        bootAppServer();
        waitForServer(1000);
    }

    private static void insertTestData() {
        LOG.warn("Inserting test data!!!!");
        RouteTestUtil.setupDatabasePool();
        TestDataSetupUtil.insertStaticTestData();
        initializeStaticTestUserData();
        LOG.warn("Test data has been inserted!!!!");
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
                LOG.info("Inserted test client {}", insertedClientId);
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
                    LOG.warn("Could not find test user {}", testUserGuid);
                }
                int numRowsUpdated = jdbiUser.updateAuth0Id(testUserGuid, testUserAuth0Id);
                if (numRowsUpdated != 1) {
                    LOG.error("Updated {} rows to for test user {}", numRowsUpdated, testUserGuid);
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
        // todo(yufeng) figure out how to shutdown server running in separate process
        DataDonationPlatform.shutdown();
        TransactionWrapper.reset();
    }

    private static void bootAppServer() {
        Config cfg = RouteTestUtil.getConfig();
        int port = cfg.getInt(ConfigFile.PORT);
        boolean spawnProcess = cfg.getBoolean(ConfigFile.BOOT_TEST_APP_IN_SEPARATE_PROCESS);

        Map<String, String> serverArgs = new HashMap<>();
        serverArgs.put("config.file", RouteTestUtil.getConfigFilePath());

        int bootTimeoutSeconds = 30;
        LOG.info("Starting app on port {}", port);

        if (spawnProcess) {
            if (isDebugEnabled()) {
                LOG.warn("You're running in debug mode but the server side is running in a separate process.  "
                        + "You will need to set the debug port for the server side separately and connect "
                        + "the debugger in a separate remote session.  Alternatively, run the server side in-process");
            }
            try {
                // todo arz parameterize/environment-ize debug port
                JavaProcessSpawner.spawnMainInSeparateProcess(DataDonationPlatform.class,
                        IntegrationTestSuite.class, bootTimeoutSeconds, null, serverArgs);
            } catch (IOException e) {
                LOG.error("App starter failed.", e);
            }
        } else {
            runDdpServer();
        }

    }

    private static boolean isDebugEnabled() {
        boolean isDebugOn = false;
        RuntimeMXBean mxBean = ManagementFactory.getRuntimeMXBean();
        if (mxBean != null) {
            List<String> args = mxBean.getInputArguments();
            if (args != null) {
                for (String arg : args) {
                    if (arg.contains(DEBUG_FLAG)) {
                        isDebugOn = true;
                        break;
                    }
                }
            }
        }
        return isDebugOn;
    }

    private static void runDdpServer() {
        long startTime = System.currentTimeMillis();
        LOG.info("Booting ddp...");

        DataDonationPlatform.main(new String[] {});

        LOG.info("It took {}ms to start ddp", (System.currentTimeMillis() - startTime));
    }

    private static void waitForServer(int millisecs) {
        try {
            LOG.info("Pausing for {}ms for server to stabilize", millisecs);
            Thread.sleep(millisecs);
        } catch (InterruptedException e) {
            LOG.info("Wait interrupted", e);
        }
    }

    /**
     * Base class that provides the hooks back into the test suite in order to allow a single test
     * class or test method to run within an initialized environment for route testing.
     */
    public abstract static class TestCase {
        @BeforeClass
        public static void doSuiteSetup() {
            IntegrationTestSuite.setup();
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
            return true;
        } catch (HttpHostConnectException e) {
            LOG.debug("Could not connect to server url. Must not be running");
            return false;
        } catch (IOException e) {
            String msg = "There was problem initializing CloseableHttpClient";
            LOG.error(msg, e);
            throw new DDPException(msg, e);
        }

    }

    public static void main(String[] args) {
        initializeDatabase();
        startupTestServer();
        insertTestData();
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
    }

}
