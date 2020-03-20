package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_EMAIL;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_PASSWORD;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_USER_GUID;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_DAY;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_MONTH;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_YEAR;
import static org.broadinstitute.ddp.constants.TestConstants.getTestStudyBloodPexEXPR;
import static org.broadinstitute.ddp.constants.TestConstants.getTestStudyTissuePexEXPR;
import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.broadinstitute.ddp.model.activity.types.TextInputType.TEXT;
import static org.broadinstitute.ddp.util.GuidUtils.UPPER_ALPHA_NUMERIC;
import static org.broadinstitute.ddp.util.TestUtil.wrapQuestions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.sql.Driver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.auth0.exception.Auth0Exception;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDao;
import org.broadinstitute.ddp.db.UserDaoFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatusType;
import org.broadinstitute.ddp.db.dao.JdbiActivityStatusTrigger;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiDsmNotificationEventType;
import org.broadinstitute.ddp.db.dao.JdbiDsmNotificationTrigger;
import org.broadinstitute.ddp.db.dao.JdbiEventAction;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dao.JdbiEventTrigger;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiSendgridConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrella;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.SendgridConfigurationDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.pdf.ActivityDateSubstitution;
import org.broadinstitute.ddp.model.pdf.AnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.BooleanAnswerSubstitution;
import org.broadinstitute.ddp.model.pdf.CustomTemplate;
import org.broadinstitute.ddp.model.pdf.PdfActivityDataSource;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.model.study.ActivityMapping;
import org.broadinstitute.ddp.model.study.ActivityMappingType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.OLCService;
import org.jdbi.v3.core.Handle;
import org.slf4j.LoggerFactory;

/**
 * Utility that helps setup general purpose generatedTestData for testing.
 */
public class TestDataSetupUtil {

    public static final String TEMP_DISABLE_CLIENT_STUDY_TENANT_CONSTRAINTS =
            "src/test/resources/db-testscripts/disable-tenant-constraints.xml";
    public static final String MIGRATE_LEGACY_STUDY_CLIENT_TENANT_AND_ENABLE_CONSTRAINTS =
            "src/test/resources/db-testscripts/backfill-test-tenants-and-re-enable-tenant-constrains.xml";
    public static final String BASELINE_SEED_TEST_DATA =
            "src/test/resources/db-testscripts/baseline-seed-test-data.xml";
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(TestDataSetupUtil.class);
    private static final Config cfg = ConfigManager.getInstance().getConfig();
    private static final Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
    private static final Config sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);
    private static final String testUser = auth0Config.getString(AUTH0_TEST_EMAIL);
    private static final String testUserGuid = auth0Config.getString(AUTH0_TEST_USER_GUID);
    private static final String password = auth0Config.getString(AUTH0_TEST_PASSWORD);
    private static final List<GeneratedTestData> testDataToDelete = new ArrayList<>();
    private static final String CONSENT_PDF_LOCATION = "src/test/resources/ConsentForm.pdf";
    private static UserDao userDao = UserDaoFactory.createFromSqlConfig(sqlConfig);

    public static void main(String[] args) throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.ERROR);
        Options options = new Options();

        options.addOption("s", true, "Study Guid");
        options.addOption("c", true, "Auth0 ClientId");
        options.addOption("n", true, "Number of Users to Create");
        options.addOption("p", false, "Should create random profiles");
        options.addOption("m", false, "Should create mail address");
        options.addOption("a", false, "Should create random activities");
        options.addOption("r", false, "Should randomly complete random activities");
        options.addOption("i", false, "Should create random institutions");


        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        Config cfg = ConfigManager.getInstance().getConfig();
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);

        String backendTestAuth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
        String backendTestDomain = auth0Config.getString(ConfigFile.DOMAIN);

        initializeDb(cfg);
        TransactionWrapper.useTxn(handle -> {
            try {
                String studyGuid;
                StudyDto studyDto;
                if (cmd.hasOption('s')) {
                    studyGuid = cmd.getOptionValue('s');
                    studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                    if (studyDto == null) {
                        throw new Exception("Can't generate Test Data, study guid does not exist: " + studyGuid);
                    }
                } else {
                    studyDto = generateTestStudy(handle, cfg);
                    studyGuid = studyDto.getGuid();
                }

                System.out.println("--- USING STUDY: " + studyDto.getGuid());

                GeneratedTestData testActivityData = null;
                if (cmd.hasOption('a')) {
                    testActivityData = new GeneratedTestData();
                    testActivityData.setTestingStudy(studyDto);
                    addTestConsent(handle, testActivityData);
                    System.out.println("--- SETTING TEST ACTIVITIES, ACTIVITY INSTANCE ID: " + testActivityData.getConsentActivityId());
                    addEnrollmentHook(handle, testActivityData);
                    System.out.println("--- ADDING ENROLLMENT HOOK, ENROLLMENT CFG ID: " + testActivityData.getEnrollmentConfigurationId());
                }

                String auth0ClientId;
                if (cmd.hasOption('c')) {
                    auth0ClientId = cmd.getOptionValue('c');
                } else {
                    auth0ClientId = backendTestAuth0ClientId;
                }

                JdbiClient jdbiClient = handle.attach(JdbiClient.class);
                StudyClientConfiguration studyClientConfiguration =
                        jdbiClient.getStudyClientConfigurationByClientAndDomain(auth0ClientId, backendTestDomain)
                                .orElseThrow(() -> new Exception("Could not find client: " + auth0ClientId));

                System.out.println("--- USING AUTH0CLIENTID: " + studyClientConfiguration.getAuth0ClientId());

                if (cmd.hasOption('n')) {
                    int numberOfUsersToCreate = Integer.parseInt(cmd.getOptionValue('n'));
                    System.out.println(String.format("--- CREATING %s USERS: ", numberOfUsersToCreate));
                    System.out.print("USER_ID\tFIRST_NAME\tLAST_NAME\tSEX\tBIRTHDATE");
                    if (cmd.hasOption('r') && cmd.hasOption('p')) {
                        System.out.println("\tCOMPLETED");
                    } else {
                        System.out.println();
                    }
                    for (int i = 0; i < numberOfUsersToCreate; i++) {
                        JdbiUser userDao = handle.attach(JdbiUser.class);

                        String userGuid = DBUtils.uniqueUserGuid(handle);
                        String userHruid = DBUtils.uniqueUserHruid(handle);
                        long userId = userDao.insert(null, userGuid, studyClientConfiguration.getClientId(), userHruid);

                        handle.attach(JdbiUserStudyEnrollment.class)
                                .changeUserStudyEnrollmentStatus(userGuid, studyGuid, EnrollmentStatusType.REGISTERED);
                        Auth0Util.TestingUser testingUser = new Auth0Util.TestingUser(userId,
                                userGuid,
                                userHruid,
                                null,
                                null,
                                null,
                                null);

                        System.out.print(String.valueOf(userId) + "\t");
                        UserProfile profile = null;
                        if (cmd.hasOption('p')) {
                            profile = createTestingProfile(handle, userId, true);
                            LocalDate birthDate = profile.getBirthDate();
                            System.out.print(String.format("%s\t%s\t%s\t%s\t", profile.getFirstName(),
                                    profile.getLastName(),
                                    profile.getSexType().name(),
                                    DateTimeFormatter.ISO_DATE.format(birthDate)));
                        }

                        GeneratedTestData generatedTestData = new GeneratedTestData(testingUser, profile,
                                studyDto, studyClientConfiguration);
                        if (testActivityData != null) {
                            generatedTestData.mergeTestActivityData(testActivityData);
                        }

                        if (cmd.hasOption("i")) {
                            createTestMedicalProvider(handle, generatedTestData, true);
                        }

                        if (cmd.hasOption('m')) {
                            createTestingMailAddress(handle, generatedTestData);
                        }
                        Random random = new Random();

                        if (cmd.hasOption('r')) {
                            if (!cmd.hasOption('p')) {
                                throw new Exception("You can't very well answer questions without a profile. "
                                        + "Choose -p!");
                            }
                            boolean completedRelease = random.nextBoolean();
                            answerTestConsent(handle, completedRelease, random.nextBoolean(), random.nextBoolean(),
                                    TEST_USER_PROFILE_BIRTH_DAY,
                                    TEST_USER_PROFILE_BIRTH_MONTH,
                                    TEST_USER_PROFILE_BIRTH_YEAR,
                                    generatedTestData);

                            if (completedRelease) {
                                System.out.println("COMPLETED CONSENT");
                            } else {
                                System.out.println("DID NOT COMPLETE CONSENT");
                            }
                        } else {
                            System.out.println();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    private static void initializeDb(Config cfg) {
        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String dbUrl = cfg.getString(ConfigFile.DB_URL);

        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));
    }

    public static GeneratedTestData generateBasicUserTestData(Handle handle) {
        return generateBasicUserTestData(handle, false);
    }

    public static GeneratedTestData generateBasicUserTestData(Handle handle, boolean forceUserCreation) {
        String sendgridApiKey = cfg.getString(ConfigFile.SENDGRID_API_KEY);
        String backendTestAuth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
        String backendTestSecret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
        String backendTestClientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
        String auth0domain = auth0Config.getString(ConfigFile.DOMAIN);
        String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
        String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
        String encryptionSecret = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);

        return generateBasicUserTestData(
                handle,
                forceUserCreation,
                auth0domain,
                backendTestClientName,
                backendTestAuth0ClientId,
                backendTestSecret,
                encryptionSecret,
                mgmtClientId,
                mgmtSecret,
                sendgridApiKey
        );

    }

    /**
     * Sets up generally useful high level test generatedTestData in the database,
     * including a full user id, umbrella, and study. User generation is only
     * done once per VM so that we don't create a bajillion test
     * users in auth0. Once the test user has been created once in the VM,
     * the cached copy is returned for all tests, along with freshly generated study.
     */
    public static synchronized GeneratedTestData generateBasicUserTestData(Handle handle,
                                                                           boolean forceUserCreation,
                                                                           String auth0Domain,
                                                                           String auth0clientName,
                                                                           String auth0clientId,
                                                                           String auth0Secret,
                                                                           String encryptionSecret,
                                                                           String mgmtClientId,
                                                                           String mgmtSecret,
                                                                           String sendgridApiKey) {

        StudyDto study = generateTestStudy(handle, auth0Domain, mgmtClientId, mgmtSecret);

        configureStudyForSendgrid(handle, sendgridApiKey, study);

        JdbiClient jdbiClient = handle.attach(JdbiClient.class);
        StudyClientConfiguration studyClientConfiguration =
                jdbiClient.getStudyClientConfigurationByClientAndDomain(auth0clientId, auth0Domain).orElse(null);

        long clientId;
        if (studyClientConfiguration == null) {
            // Insert client and grant study access.
            ClientDao clientDao = handle.attach(ClientDao.class);
            JdbiAuth0Tenant jdbiAuth0Tenant = handle.attach(JdbiAuth0Tenant.class);
            String encryptedSecret = AesUtil.encrypt(mgmtSecret, EncryptionKey.getEncryptionKey());
            Auth0TenantDto auth0Tenant = jdbiAuth0Tenant.insertIfNotExists(auth0Domain,
                    mgmtClientId,
                    encryptedSecret);
            clientId = clientDao.registerClient(
                    auth0clientName,
                    auth0clientId,
                    auth0Secret,
                    Collections.singletonList(study.getGuid()),
                    encryptionSecret,
                    auth0Tenant.getId());

            studyClientConfiguration = new StudyClientConfiguration(
                    clientId,
                    auth0Domain,
                    auth0clientId,
                    AesUtil.encrypt(auth0Secret, EncryptionKey.getEncryptionKey()));
        } else {
            // Just grant access to generated study.
            clientId = studyClientConfiguration.getClientId();
            handle.attach(JdbiClientUmbrellaStudy.class).insert(clientId, study.getId());
        }

        GeneratedTestData generatedTestData = null;
        Auth0Util.TestingUser testUserBeingUsed = null;
        UserProfile profile = null;

        if (!forceUserCreation) {
            Long userId = null;
            try {
                UserDto user = handle.attach(JdbiUser.class).findByUserGuid(testUserGuid);
                if (user == null) {
                    // TODO Separate Unit Tests and Integration Tests. Units should not touch the DB
                    LOG.error("Missing canonical test user. This must be a TXNAwareTest which is not an "
                            + "integration test");
                    String testUserAuth0Id = ConfigManager.getInstance().getConfig().getConfig(ConfigFile.AUTH0).getString(
                            ConfigFile.TEST_USER_AUTH0_ID);
                    userId = TestingUserUtil.createCanonicalTestUser(handle,
                            studyClientConfiguration.getClientId(),
                            testUserAuth0Id,
                            testUserGuid,
                            TestConstants.TEST_USER_HRUID);
                }

                testUserBeingUsed = TestingUserUtil.loginExistingTestingUser(
                        handle,
                        testUser,
                        password,
                        testUserGuid,
                        auth0clientName,
                        auth0clientId,
                        auth0Secret,
                        study.getGuid()
                );

                if (userId != null) {
                    testUserBeingUsed.setUserId(userId);
                }
            } catch (Auth0Exception e) {
                // This is a test, so this is ok.
                if (e.getMessage().toLowerCase().contains("too many logins")) {
                    throw new RuntimeException("Too many logins with " + testUser + " on " + auth0Domain + ".  You "
                            + "may need to remove cached token files from "
                            + System.getProperty("java.io.tmpdir"), e);
                }
                throw new RuntimeException(e);
            }

            profile = handle.attach(UserProfileDao.class).findProfileByUserGuid(testUserGuid).orElse(null);
            if (profile == null) {
                profile = createTestingProfile(handle, testUserBeingUsed.getUserId(), false);
            }

            generatedTestData = new GeneratedTestData(testUserBeingUsed, profile, study, studyClientConfiguration);
        } else {
            try {
                testUserBeingUsed = generateTestUser(handle, auth0Domain,
                        auth0clientName,
                        auth0clientId,
                        auth0Secret,
                        study.getGuid());

                profile = createTestingProfile(handle, testUserBeingUsed.getUserId(), false);

            } catch (Auth0Exception e) {
                throw new RuntimeException("Could not generate testing user", e);
            }

            generatedTestData = new GeneratedTestData(testUserBeingUsed, profile, study, studyClientConfiguration);
            testDataToDelete.add(generatedTestData);
        }


        return generatedTestData;
    }

    private static void configureStudyForSendgrid(Handle handle, String sendgridApiKey, StudyDto study) {
        JdbiSendgridConfiguration jdbiSendgridConfig = handle.attach(JdbiSendgridConfiguration.class);
        SendgridConfigurationDto sendgridConfigurationDto = jdbiSendgridConfig.findByStudyGuid(study.getGuid())
                .orElse(null);
        if (sendgridConfigurationDto == null) {
            long sendgridConfigurationId = jdbiSendgridConfig.insert(study.getId(), sendgridApiKey, "Testing",
                    "testing@datadonationplatform.org", "Hello test patient,");

        }
    }

    /**
     * Inserts the environment-sensitive tenant used for testing
     */
    private static void insertTestingTenant(Handle handle, Config auth0Cfg) {
        JdbiAuth0Tenant jdbiAuth0Tenant = handle.attach(JdbiAuth0Tenant.class);
        String domain = auth0Cfg.getString(ConfigFile.DOMAIN);
        String mgmtApiClient = auth0Cfg.getString(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID);
        String mgmtSecret = auth0Cfg.getString(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET);

        String encryptedSecret = AesUtil.encrypt(mgmtSecret, EncryptionKey.getEncryptionKey());
        jdbiAuth0Tenant.insertIfNotExists(domain, mgmtApiClient, encryptedSecret);
        LOG.info("Inserted testing tenant {}", domain);
    }

    /**
     * Setup database with hardcoded test data like test umbrella, study, user, and client.
     */
    public static synchronized void insertStaticTestData() {
        Config cfg = ConfigManager.getInstance().getConfig();
        TransactionWrapper.useTxn(handle -> {
            insertTestingTenant(handle, cfg.getConfig(ConfigFile.AUTH0));
        });
        List<String> scripts = new ArrayList<>();
        // temporarily disable client/study tenant relationships so that we can add
        // legacy seed test data to existing dbs without requiring a drop/reload
        scripts.add(TEMP_DISABLE_CLIENT_STUDY_TENANT_CONSTRAINTS);
        scripts.add(BASELINE_SEED_TEST_DATA);
        scripts.add(MIGRATE_LEGACY_STUDY_CLIENT_TENANT_AND_ENABLE_CONSTRAINTS);
        try {
            Driver driver = new com.mysql.jdbc.Driver();

            for (String script : scripts) {
                LOG.info("Running legacy test setup script {}", script);
                String dbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());
                LiquibaseUtil.runChangeLog(driver, dbUrl, script);
            }
        } catch (Exception e) {
            LOG.error("Failed to insert static test account data", e);
        }
    }

    public static StudyDto generateTestStudy(Handle handle, Config topLevelConfig) {
        Config auth0Config = topLevelConfig.getConfig(ConfigFile.AUTH0);
        String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
        String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
        return generateTestStudy(handle, auth0Domain, mgmtClientId, mgmtSecret);
    }

    public static StudyDto generateTestStudy(Handle handle, String auth0Domain, String mgmtClientId,
                                             String mgmtSecret) {
        String umbrellaName = TestConstants.BACKEND_BASE_TEST_UMBRELLA
                + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10);
        long umbrellaId = handle.attach(JdbiUmbrella.class)
                .insert(umbrellaName, umbrellaName.toLowerCase());

        OLCPrecision studyPrecision = OLCPrecision.MEDIUM;
        boolean shareParticipantLocation = true;
        return generateTestStudy(handle, auth0Domain, mgmtClientId, mgmtSecret, studyPrecision, shareParticipantLocation, umbrellaId);
    }

    public static StudyDto generateTestStudy(Handle handle, String auth0Domain, String mgmtClientId, String
            mgmtSecret, OLCPrecision studyPrecision, boolean shareParticipantLocation,
                                             long umbrellaId) {

        String studyGuid = DBUtils.uniqueStandardGuid(handle,
                SqlConstants.UmbrellaStudyTable.TABLE_NAME, SqlConstants.UmbrellaStudyTable.GUID);
        String studyName = TestConstants.BACKEND_BASE_TEST_STUDY_NAME
                + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10);
        String webBaseUrl = "https://fake.datadonationplatform.org";

        JdbiAuth0Tenant jdbiAuth0Tenant = handle.attach(JdbiAuth0Tenant.class);

        String encryptedSecret = AesUtil.encrypt(mgmtSecret, EncryptionKey.getEncryptionKey());
        Auth0TenantDto auth0TenantDto = jdbiAuth0Tenant.insertIfNotExists(auth0Domain,
                mgmtClientId,
                encryptedSecret);

        long studyId = handle.attach(JdbiUmbrellaStudy.class).insert(studyName, studyGuid, umbrellaId, webBaseUrl,
                auth0TenantDto.getId(), studyPrecision, shareParticipantLocation, null);
        return new StudyDto(studyId, studyGuid, studyName, null, webBaseUrl, umbrellaId, auth0TenantDto.getId(),
                studyPrecision,
                shareParticipantLocation, null, false);
    }

    public static FormActivityDef generateTestFormActivityForUser(Handle handle, String userGuid, String studyGuid) {
        FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
        JdbiRevision revisionDao = handle.attach(JdbiRevision.class);
        JdbiUser userDao = handle.attach(JdbiUser.class);
        long millis = Instant.now().toEpochMilli();
        String activityCode = "DSM_NOTIFICATION_TEST_ACTIVITY" + millis;
        long revisionId = revisionDao.insert(userDao.getUserIdByGuid(userGuid), millis,
                null, "dsm notification test " + millis);
        FormActivityDef formActivity = FormActivityDef.formBuilder(FormType.GENERAL, activityCode, "v1", studyGuid)
                .addName(new Translation("en", "testing"))
                .setMaxInstancesPerUser(1)
                .build();
        formActivityDao.insertActivity(formActivity, revisionId);
        return formActivity;
    }

    public static ActivityInstanceDto generateTestFormActivityInstanceForUser(Handle handle, long activityId, String userGuid) {
        ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
        return activityInstanceDao.insertInstance(activityId, userGuid, userGuid, CREATED, false);
    }

    /**
     * Generates an event configuration whose most typical use is queuing a Housekeeping event
     * This method was originally created for serving the SendDsmNotificationRouteTest, so
     * it inserts quite a lot of things. You can create a more generic method instead
     *
     * @param generatedTestData    Test data which is used for getting userGuid, studyGuid etc.
     * @param sendgridTemplateGuid GUID of the Sendgrid template used for sending an e-mail
     * @return id of the generated event configuration
     */
    public static long generateDsmNotificationTestEventConfiguration(
            Handle handle,
            TestDataSetupUtil.GeneratedTestData generatedTestData,
            String sendgridTemplateGuid
    ) {
        EventActionDao eventActionDao = handle.attach(EventActionDao.class);
        JdbiExpression expressionDao = handle.attach(JdbiExpression.class);
        JdbiEventTrigger eventTriggerDao = handle.attach(JdbiEventTrigger.class);
        JdbiActivityStatusTrigger activityStatusTriggerDao = handle.attach(JdbiActivityStatusTrigger.class);
        ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
        JdbiActivityInstanceStatusType statusTypeDao = handle.attach(JdbiActivityInstanceStatusType.class);
        JdbiEventConfiguration eventConfigDao = handle.attach(JdbiEventConfiguration.class);
        JdbiDsmNotificationEventType dsmNotificationEventTypeDao = handle.attach(JdbiDsmNotificationEventType.class);

        SendgridEmailEventActionDto eventActionDto = new SendgridEmailEventActionDto(sendgridTemplateGuid, "en");
        long testEmailActionId = eventActionDao.insertNotificationAction(eventActionDto);

        // create a new activity
        FormActivityDef testActivity = TestDataSetupUtil.generateTestFormActivityForUser(
                handle, generatedTestData.getUserGuid(), generatedTestData.getStudyGuid()
        );

        Auth0Util.TestingUser testingUser = generatedTestData.getTestingUser();
        long testingUserId = testingUser.getUserId();
        String testingUserGuid = testingUser.getUserGuid();
        LOG.info("Using generated testing user {} with guid {} and auth0 id {}", testingUserId, testingUserGuid,
                testingUser.getAuth0Id());

        // create an activity instance of the created activity for the user
        ActivityInstanceDto testActivityInstance = TestDataSetupUtil.generateTestFormActivityInstanceForUser(
                handle, testActivity.getActivityId(), testingUserGuid
        );
        LOG.info("Created activity instance {} with guid {}", testActivityInstance.getId(),
                testActivityInstance.getGuid());

        long trueExpressionId = expressionDao.insertExpression("true").getId();
        long falseExpressionId = expressionDao.insertExpression("false").getId();

        // setup a status change event trigger so that when status changes, an event is queued
        long eventTriggerId = eventTriggerDao.insert(EventTriggerType.DSM_NOTIFICATION);

        Optional<Long> dsmNotificationEventTypeId = dsmNotificationEventTypeDao.findIdByCode(JdbiDsmNotificationEventType.SALIVA_RECEIVED);

        // create an instance of the DSM notication trigger subclass
        handle.attach(JdbiDsmNotificationTrigger.class).insert(
                eventTriggerId,
                dsmNotificationEventTypeId.get()
        );

        // a user must be eligible for DSM operations
        handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                testingUserGuid,
                generatedTestData.getStudyGuid(),
                EnrollmentStatusType.ENROLLED);

        // finally, create the event configuration
        return eventConfigDao.insert(
                eventTriggerId,
                testEmailActionId,
                generatedTestData.getStudyId(),
                Instant.now().toEpochMilli(),
                1,
                null,
                trueExpressionId,
                falseExpressionId,
                true,
                1
        );
    }

    /**
     * Creates a new test user
     */
    private static Auth0Util.TestingUser generateTestUser(Handle handle,
                                                          String auth0Domain,
                                                          String auth0clientName,
                                                          String auth0clientId,
                                                          String auth0Secret,
                                                          String studyGuid) throws Auth0Exception {
        return TestingUserUtil.createAndLoginNewTestUser(handle, auth0Domain, auth0clientName, auth0clientId,
                auth0Secret, studyGuid);
    }

    public static Auth0Util.TestingUser generateTestUser(Handle handle, String studyGuid) throws Auth0Exception {
        String backendTestAuth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
        String backendTestSecret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
        String backendTestClientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
        String auth0domain = auth0Config.getString(ConfigFile.DOMAIN);

        return generateTestUser(handle, auth0domain, backendTestClientName, backendTestAuth0ClientId, backendTestSecret, studyGuid);
    }

    /**
     * Creates a profile for this user
     */
    public static UserProfile createTestingProfile(Handle handle, long userId, boolean random) {
        Random rand = new Random();
        UserProfile profile = new UserProfile.Builder(userId)
                .setFirstName(random
                        ? "John" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10)
                        : TestConstants.TEST_USER_PROFILE_FIRST_NAME)
                .setLastName(random
                        ? "Doe" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10)
                        : TestConstants.TEST_USER_PROFILE_LAST_NAME)
                .setSexType(random
                        ? UserProfile.SexType.values()[rand.nextInt(UserProfile.SexType.values().length)]
                        : TestConstants.TEST_USER_PROFILE_SEX)
                .setBirthDate(LocalDate.of(
                        random ? rand.ints(1800, 2017).findFirst().getAsInt()
                                : TestConstants.TEST_USER_PROFILE_BIRTH_YEAR,
                        random ? rand.ints(1, 12).findFirst().getAsInt()
                                : TestConstants.TEST_USER_PROFILE_BIRTH_MONTH,
                        random ? rand.ints(1, 28).findFirst().getAsInt()
                                : TestConstants.TEST_USER_PROFILE_BIRTH_DAY))
                .setPreferredLangId(handle.attach(JdbiLanguageCode.class)
                        .getLanguageCodeId(TestConstants.TEST_USER_PROFILE_PREFERRED_LANGUAGE))
                .build();
        handle.attach(UserProfileDao.class).createProfile(profile);
        return profile;
    }

    /**
     * Creates a mail address for this user
     */
    public static MailAddress createTestingMailAddress(Handle handle, GeneratedTestData generatedTestData) {
        MailAddress testAddress = new MailAddress(
                TestConstants.TEST_USER_PROFILE_FIRST_NAME + " " + TestConstants.TEST_USER_PROFILE_LAST_NAME,
                TestConstants.TEST_USER_MAIL_ADDRESS_STREET_NAME,
                TestConstants.TEST_USER_MAIL_ADDRESS_STREET_APT,
                TestConstants.TEST_USER_MAIL_ADDRESS_CITY,
                TestConstants.TEST_USER_MAIL_ADDRESS_STATE,
                TestConstants.TEST_USER_MAIL_ADDRESS_COUNTRY,
                TestConstants.TEST_USER_MAIL_ADDRESS_ZIP,
                TestConstants.TEST_USER_MAIL_ADDRESS_PHONE,
                null,
                null,
                DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS,
                true);

        testAddress.setPlusCode(new OLCService(cfg.getString(ConfigFile.GEOCODING_API_KEY)).calculateFullPlusCode(testAddress));

        JdbiMailAddress dao = handle.attach(JdbiMailAddress.class);
        dao.insertAddress(testAddress,
                generatedTestData.getTestingUser().getUserGuid(),
                generatedTestData.getUserGuid());
        dao.setDefaultAddressForParticipant(testAddress.getGuid());
        generatedTestData.setMailAddress(testAddress);

        return testAddress;
    }

    public static void deleteEnrollmentHook(Handle handle, GeneratedTestData generatedTestData) {
        handle.attach(JdbiEventConfigurationOccurrenceCounter.class).deleteById(generatedTestData.getEnrollmentConfigurationId(),
                generatedTestData.getUserId());
        handle.attach(JdbiEventConfiguration.class).deleteById(generatedTestData.getEnrollmentConfigurationId());
        handle.attach(JdbiActivityStatusTrigger.class).deleteById(generatedTestData.getConsentActivityStatusTriggerId());
        handle.attach(JdbiEventTrigger.class).deleteById(generatedTestData.getEnrollmentEventTriggerId());
        handle.attach(JdbiEventAction.class)
                .deleteById(generatedTestData.getEnrollmentActionId());
    }

    public static void addEnrollmentHook(Handle handle, GeneratedTestData generatedTestData) throws Exception {
        if (generatedTestData.getConsentActivityId() == null) {
            throw new Exception("You need to define a consentActivity in the generatedTestData");
        }

        generatedTestData.setEnrollmentActionId(handle.attach(JdbiEventAction.class)
                .insert(null, EventActionType.USER_ENROLLED));

        generatedTestData.setEnrollmentEventTriggerId(handle.attach(JdbiEventTrigger.class).insert(EventTriggerType.ACTIVITY_STATUS));

        generatedTestData.setConsentActivityStatusTriggerId(generatedTestData.getEnrollmentEventTriggerId());

        handle.attach(JdbiActivityStatusTrigger.class).insert(
                generatedTestData.getEnrollmentEventTriggerId(),
                generatedTestData.getConsentActivityId(),
                InstanceStatusType.COMPLETE);

        generatedTestData.setEnrollmentConfigurationId(handle.attach(JdbiEventConfiguration.class).insert(
                generatedTestData.getEnrollmentEventTriggerId(),
                generatedTestData.getEnrollmentActionId(),
                generatedTestData.getStudyId(),
                Instant.now().toEpochMilli(),
                null,
                null,
                null,
                null,
                false,
                1
        ));
    }

    public static void answerTestConsent(Handle handle,
                                         boolean hasGivenInformedConsent,
                                         boolean hasConsentedToBlood,
                                         boolean hasConsentedToTissue,
                                         int birthDay,
                                         int birthMonth,
                                         int birthYear,
                                         GeneratedTestData generatedTestData) throws Exception {
        if (generatedTestData.getConsentActivityId() == null) {
            throw new Exception("You need to define a consentActivity in the generatedTestData");
        }

        if (hasConsentedToBlood || hasConsentedToTissue) {
            assert (hasGivenInformedConsent);
        }

        ActivityInstanceDto instance = handle.attach(ActivityInstanceDao.class).insertInstance(generatedTestData.getConsentActivityId(),
                generatedTestData.getUserGuid());
        generatedTestData.setConsentActivityInstanceGuid(instance.getGuid());

        AnswerDao answerDao = handle.attach(AnswerDao.class);
        if (hasGivenInformedConsent) {
            Answer informedConsentAnswer = new TextAnswer(null, generatedTestData.getSignatureQuestionStableId(), null,
                    generatedTestData.getProfile().getFirstName() + " " + generatedTestData.getProfile().getLastName());
            answerDao.createAnswer(generatedTestData.getUserId(), instance.getId(), informedConsentAnswer);

            Answer bloodAnswer = new BoolAnswer(null, generatedTestData.getBloodQuestionStableId(), null,
                    hasConsentedToBlood);
            answerDao.createAnswer(generatedTestData.getUserId(), instance.getId(), bloodAnswer);

            Answer tissueAnswer = new BoolAnswer(null, generatedTestData.getTissueQuestionStableId(),
                    null, hasConsentedToTissue);
            answerDao.createAnswer(generatedTestData.getUserId(), instance.getId(), tissueAnswer);

            FormActivityStatusUtil.updateFormActivityStatus(handle, InstanceStatusType.COMPLETE,
                    generatedTestData.getConsentActivityInstanceGuid(), generatedTestData.getUserGuid());
        }

        Answer birthDateAnswer = new DateAnswer(null, generatedTestData.getDateOfBirthStableId(), null,
                birthYear, birthMonth, birthDay);
        answerDao.createAnswer(generatedTestData.getUserId(), instance.getId(), birthDateAnswer);
    }

    public static void answerAboutYou(Handle handle,
                                      long diagnosisMillisSinceEpoch,
                                      GeneratedTestData generatedTestData) throws Exception {
        if (generatedTestData.getAboutYouActivityId() == null) {
            throw new Exception("You need to define an aboutYou activity in the generatedTestData");
        }

        ActivityInstanceDto instance = handle.attach(ActivityInstanceDao.class).insertInstance(generatedTestData.getAboutYouActivityId(),
                generatedTestData.getUserGuid());
        generatedTestData.setAboutYouActivityInstanceGuid(instance.getGuid());

        DateAnswer dateOfDiagnosisAnswer = new DateAnswer(null, generatedTestData.getDateOfDiagnosisStableId(),
                null, DateValue.fromMillisSinceEpoch(diagnosisMillisSinceEpoch));

        handle.attach(AnswerDao.class).createAnswer(generatedTestData.getUserId(), instance.getId(), dateOfDiagnosisAnswer);

        FormActivityStatusUtil.updateFormActivityStatus(handle, InstanceStatusType.COMPLETE,
                generatedTestData.getAboutYouActivityInstanceGuid(), generatedTestData.getUserGuid());
    }

    public static void addAboutYou(Handle handle, GeneratedTestData generatedTestData) {
        UserDto testAdministrator = handle.attach(JdbiUser.class).findByUserGuid(testUserGuid);

        String aboutYouActivityCode = GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20);
        generatedTestData.setDateOfDiagnosisStableId("DATE_OF_DIAGNOSIS_"
                + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20));

        DateQuestionDef dateOfDiagnosisQuestion = DateQuestionDef.builder(
                DateRenderMode.TEXT,
                generatedTestData.getDateOfDiagnosisStableId(),
                textTmpl("When were you first diagnosed?"))
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .build();

        FormSectionDef formSectionDef = new FormSectionDef(null,
                wrapQuestions(dateOfDiagnosisQuestion));


        ActivityDao activityDao = handle.attach(ActivityDao.class);
        FormActivityDef aboutYou = FormActivityDef.generalFormBuilder(aboutYouActivityCode,
                "v1",
                generatedTestData.getStudyGuid())
                .addSection(formSectionDef)
                .addName(new Translation("en", "About You Activity"))
                .build();

        ActivityVersionDto activityVersionDto =
                activityDao.insertActivity(aboutYou, RevisionMetadata.now(testAdministrator.getUserId(),
                        "add " + aboutYouActivityCode));

        generatedTestData.setAboutYouActivityId(activityVersionDto.getActivityId());

        // Create consent mappings for this study
        activityDao.insertActivityMapping(new ActivityMapping(generatedTestData.getStudyGuid(),
                ActivityMappingType.DATE_OF_DIAGNOSIS,
                activityVersionDto.getActivityId(),
                generatedTestData.getDateOfDiagnosisStableId()));
    }

    public static void addTestConsent(Handle handle, GeneratedTestData generatedTestData) {
        UserDto testAdministrator = handle.attach(JdbiUser.class).findByUserGuid(testUserGuid);

        String consentActivityCode = GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20);
        generatedTestData.setBloodQuestionStableId("BLOOD_" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20));
        generatedTestData.setTissueQuestionStableId("TISSUE_" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20));
        generatedTestData.setDateOfBirthStableId("BIRTH_DATE_" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20));
        generatedTestData.setSignatureQuestionStableId("INFORMED_CONSENT_" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20));

        // Define your questions and form definition
        BoolQuestionDef bloodQuestion = BoolQuestionDef.builder(generatedTestData.getBloodQuestionStableId(),
                textTmpl("Do you consent to blood draw?"),
                textTmpl("Yes"),
                textTmpl("No")).build();

        BoolQuestionDef tissueQuestion = BoolQuestionDef.builder(generatedTestData.getTissueQuestionStableId(),
                textTmpl("Do you consent to tissue sample?"),
                textTmpl("Yes"),
                textTmpl("No")).build();

        String var = "prompt_" + generatedTestData.getDateOfBirthStableId();
        Template prompt = Template.html("$" + var + "");
        prompt.addVariable(TemplateVariable.single(var, "en", "Date of birth"));
        DateQuestionDef birthDateQuestion = DateQuestionDef
                .builder(DateRenderMode.TEXT, generatedTestData.getDateOfBirthStableId(), prompt)
                .setDisplayCalendar(false)
                .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR)
                .setHideNumber(true)
                .build();


        TextQuestionDef signatureQuestion = TextQuestionDef.builder(TEXT,
                generatedTestData.getSignatureQuestionStableId(),
                textTmpl("Signature goes here")).build();

        FormSectionDef formSectionDef = new FormSectionDef(null,
                wrapQuestions(bloodQuestion, tissueQuestion, birthDateQuestion, signatureQuestion));


        ActivityDao activityDao = handle.attach(ActivityDao.class);
        ConsentActivityDef consent = ConsentActivityDef.builder(consentActivityCode,
                "v1",
                generatedTestData.getStudyGuid(),
                TestConstants.getTestStudyOverallPexEXPR(generatedTestData.getStudyGuid(),
                        consentActivityCode,
                        generatedTestData.getSignatureQuestionStableId()))
                .addElection(new ConsentElectionDef(generatedTestData.getBloodQuestionStableId(),
                        getTestStudyBloodPexEXPR(generatedTestData.getStudyGuid(), consentActivityCode,
                                generatedTestData.getBloodQuestionStableId())))
                .addElection(new ConsentElectionDef(generatedTestData.getTissueQuestionStableId(),
                        getTestStudyTissuePexEXPR(generatedTestData.getStudyGuid(), consentActivityCode,
                                generatedTestData.getTissueQuestionStableId())))
                .addSection(formSectionDef)
                .addName(new Translation("en", "Consent Activity"))
                .build();

        ActivityVersionDto activityVersionDto =
                activityDao.insertConsent(consent, RevisionMetadata.now(testAdministrator.getUserId(),
                        "add " + consentActivityCode));

        generatedTestData.setConsentActivityId(consent.getActivityId());
        generatedTestData.setConsentActivityCode(consentActivityCode);
        generatedTestData.setConsentVersionId(activityVersionDto.getId());

        // Create consent mappings for this study
        activityDao.insertActivityMapping(new ActivityMapping(generatedTestData.getStudyGuid(),
                ActivityMappingType.BLOOD,
                activityVersionDto.getActivityId(),
                generatedTestData.getBloodQuestionStableId()));

        activityDao.insertActivityMapping(new ActivityMapping(generatedTestData.getStudyGuid(),
                ActivityMappingType.TISSUE,
                activityVersionDto.getActivityId(),
                generatedTestData.getTissueQuestionStableId()));

        activityDao.insertActivityMapping(new ActivityMapping(generatedTestData.getStudyGuid(),
                ActivityMappingType.MEDICAL_RELEASE,
                activityVersionDto.getActivityId(),
                null));

        activityDao.insertActivityMapping(new ActivityMapping(generatedTestData.getStudyGuid(),
                ActivityMappingType.DATE_OF_BIRTH,
                activityVersionDto.getActivityId(),
                generatedTestData.getDateOfBirthStableId()));
    }

    public static PdfConfigInfo createAngioConsentPdfForm(Handle handle,
                                                          GeneratedTestData generatedTestData) throws Exception {
        FileInputStream input = new FileInputStream(CONSENT_PDF_LOCATION);
        byte[] fileContents = IOUtils.toByteArray(input);
        input.close();

        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(generatedTestData.getUserGuid());
        long revId = handle.attach(JdbiRevision.class).insertStart(Instant.now().toEpochMilli(), userId,
                "Made angio test consent pdf");

        List<String> fieldValues = Arrays.asList(ConsentFields.DRAW_BLOOD_YES,
                ConsentFields.DRAW_BLOOD_NO,
                ConsentFields.TISSUE_SAMPLE_YES,
                ConsentFields.TISSUE_SAMPLE_NO,
                ConsentFields.FULL_NAME,
                ConsentFields.DATE_OF_BIRTH,
                ConsentFields.TODAY_DATE);

        long consentActivityId = generatedTestData.getConsentActivityId();

        CustomTemplate customTemplate = new CustomTemplate(fileContents);
        customTemplate.addSubstitution(new BooleanAnswerSubstitution(fieldValues.get(0),
                consentActivityId, generatedTestData.getBloodQuestionStableId(), false));
        customTemplate.addSubstitution(new BooleanAnswerSubstitution(fieldValues.get(1),
                consentActivityId, generatedTestData.getBloodQuestionStableId(), true));
        customTemplate.addSubstitution(new BooleanAnswerSubstitution(fieldValues.get(2),
                consentActivityId, generatedTestData.getTissueQuestionStableId(), false));
        customTemplate.addSubstitution(new BooleanAnswerSubstitution(fieldValues.get(3),
                consentActivityId, generatedTestData.getTissueQuestionStableId(), true));
        customTemplate.addSubstitution(new AnswerSubstitution(fieldValues.get(4),
                consentActivityId, QuestionType.TEXT, generatedTestData.getSignatureQuestionStableId()));
        customTemplate.addSubstitution(new AnswerSubstitution(fieldValues.get(5),
                consentActivityId, QuestionType.DATE, generatedTestData.getDateOfBirthStableId()));
        customTemplate.addSubstitution(new ActivityDateSubstitution(fieldValues.get(6), consentActivityId));

        PdfConfigInfo info = new PdfConfigInfo(
                generatedTestData.getStudyId(),
                GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20));
        PdfVersion version = new PdfVersion("v1", revId);
        version.addDataSource(new PdfActivityDataSource(consentActivityId, generatedTestData.getConsentVersionId()));

        PdfConfiguration config = new PdfConfiguration(info, version);
        config.addTemplate(customTemplate);

        PdfDao pdfDao = handle.attach(PdfDao.class);
        pdfDao.insertNewConfig(config);

        return pdfDao.findConfigInfo(config.getId()).get();
    }

    private static Template textTmpl(String text) {
        return new Template(TemplateType.TEXT, null, text);
    }

    public static int createTestMedicalProvider(Handle handle, GeneratedTestData generatedTestData) {
        return createTestMedicalProvider(handle, generatedTestData, false);
    }

    public static int createTestMedicalProvider(Handle handle, GeneratedTestData generatedTestData, boolean random) {
        return createTestMedicalProvider(handle, generatedTestData, random, false);
    }

    /**
     * When your Integration Test needs a medical provider
     */
    public static int createTestMedicalProvider(Handle handle, GeneratedTestData generatedTestData, boolean random, boolean wantsAltPid) {
        Random rand = new Random();
        generatedTestData.setMedicalProvider(
                new MedicalProviderDto(null,
                        GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                        MedicalProviderUtil.getTestUserIdByGuid(handle, generatedTestData.getUserGuid()),
                        MedicalProviderUtil.getTestUmbrellaStudyIdByGuid(handle, generatedTestData.getStudyGuid()),
                        random ? InstitutionType.values()[rand.nextInt(InstitutionType.values().length)]
                                : TestConstants.TEST_INSTITUTION_TYPE,
                        random ? GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10) :
                                TestConstants.TEST_INSTITUTION_NAME,
                        random ? "Greg House" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10) :
                                TestConstants.TEST_INSTITUTION_PHYSICIAN_NAME,
                        random ? "City" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10) :
                                TestConstants.TEST_INSTITUTION_CITY,
                        random ? "State" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10) :
                                TestConstants.TEST_INSTITUTION_STATE,
                        random ? GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 5) :
                                TestConstants.TEST_INSTITUTION_ZIP,
                        random ? GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 12) :
                                TestConstants.TEST_INSTITUTION_PHONE,
                        wantsAltPid ? TestConstants.TEST_INSTITUTION_LEGACY_GUID : null,
                        random ? "Street" + GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 10) :
                                TestConstants.TEST_INSTITUTION_STREET
                ));
        return handle.attach(JdbiMedicalProvider.class).insert(generatedTestData.getMedicalProvider());
    }

    public static void deleteTestMedicalProvider(Handle handle) {
        handle.attach(JdbiMedicalProvider.class).deleteByGuid(TestConstants.TEST_INSTITUTION_GUID);
    }

    public static void deleteTestMedicalProvider(Handle handle, GeneratedTestData generatedTestData) {
        if (generatedTestData.getMedicalProvider() != null) {
            handle.attach(JdbiMedicalProvider.class).deleteByGuid(generatedTestData.getMedicalProvider().getUserMedicalProviderGuid());
        }
    }

    public static void deleteTestMailAddress(Handle handle, GeneratedTestData testData) {
        if (testData.getMailAddress() == null) {
            return;
        }

        JdbiMailAddress jdbiAddress = handle.attach(JdbiMailAddress.class);
        assertEquals(1, jdbiAddress.unsetDefaultAddressForParticipant(testData.getMailAddress().getGuid()));
        assertTrue("test mail address not deleted", jdbiAddress.deleteAddress(testData.getMailAddress().getId()));

        testData.setMailAddress(null);
    }

    public static void deleteProfile(Handle handle, GeneratedTestData generatedTestData) {
        if (generatedTestData.getProfile() != null) {
            handle.attach(UserProfileDao.class).getUserProfileSql().deleteByUserId(generatedTestData.getUserId());
        }
    }

    public static void deleteAuth0User(GeneratedTestData generatedTestData) throws Auth0Exception {
        String auth0domain = auth0Config.getString(ConfigFile.DOMAIN);
        String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
        String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);

        TestingUserUtil.deleteTestUser(generatedTestData.getTestingUser().getAuth0Id(),
                auth0domain,
                mgmtClientId,
                mgmtSecret);

    }

    public static synchronized void deleteGeneratedTestData() throws Auth0Exception {
        TransactionWrapper.useTxn(handle -> {
            for (GeneratedTestData generatedTestData : testDataToDelete) {
                deleteTestMedicalProvider(handle, generatedTestData);
                deleteTestMailAddress(handle, generatedTestData);
                deleteProfile(handle, generatedTestData);
                deleteAuth0User(generatedTestData);
            }
            testDataToDelete.clear();
        });
    }

    /**
     * Creates a new empty form activity for testing.  Activity does not have any questions.
     */
    public static FormActivityDef createBlankActivity(Handle handle, String activityCode, String userGuid, String
            studyGuid) {
        FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
        JdbiRevision revisionDao = handle.attach(JdbiRevision.class);
        JdbiUser userDao = handle.attach(JdbiUser.class);

        long millis = Instant.now().toEpochMilli();
        long revisionId = revisionDao.insert(userDao.getUserIdByGuid(userGuid), millis,
                null, "housekeeping test " + millis);
        FormActivityDef formActivity = FormActivityDef.formBuilder(FormType.GENERAL, activityCode, "v1", studyGuid)
                .addName(new Translation("en", "testing"))
                .setMaxInstancesPerUser(1)
                .build();
        formActivityDao.insertActivity(formActivity, revisionId);
        return formActivity;
    }

    public static void setUserEnrollmentStatus(Handle handle,
                                               GeneratedTestData generatedTestData,
                                               EnrollmentStatusType enrollmentStatusType) {
        handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                generatedTestData.getUserGuid(),
                generatedTestData.getStudyGuid(),
                enrollmentStatusType);

    }

    public static void deleteEnrollmentStatus(Handle handle, GeneratedTestData generatedTestData) {
        JdbiUserStudyEnrollment jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

        if (jdbiUserStudyEnrollment.getEnrollmentStatusByUserAndStudyGuids(generatedTestData.getUserGuid(),
                generatedTestData.getStudyGuid()).isPresent()) {
            Optional<EnrollmentStatusDto> enrollmentStatus = jdbiUserStudyEnrollment
                    .findByStudyGuid(generatedTestData.getStudyGuid()).stream()
                    .filter(userStudyEnrollment -> userStudyEnrollment.getUserId() == generatedTestData.getUserId())
                    .findFirst();

            if (enrollmentStatus.isPresent()) {
                jdbiUserStudyEnrollment.deleteById(enrollmentStatus.get()
                        .getUserStudyEnrollmentId());
            }
        }
    }

    public static void deleteStudyEnrollmentStatuses(Handle handle, GeneratedTestData testData) {
        handle.attach(JdbiUserStudyEnrollment.class)
                .deleteByUserGuidStudyGuid(testData.getUserGuid(), testData.getStudyGuid());
    }

    public static final class ConsentFields {
        public static final String DRAW_BLOOD_YES = "drawBlood_YES";
        public static final String DRAW_BLOOD_NO = "drawBlood_NO";
        public static final String TISSUE_SAMPLE_YES = "tissueSample_YES";
        public static final String TISSUE_SAMPLE_NO = "tissueSample_NO";
        public static final String FULL_NAME = "fullName";
        public static final String DATE_OF_BIRTH = "dateOfBirth";
        public static final String TODAY_DATE = "date";
    }

    public static class GeneratedTestData {
        private Auth0Util.TestingUser testingUser;
        private StudyDto study;
        private StudyClientConfiguration client;
        private UserProfile testUserProfile;
        private PdfConfigInfo pdfConfigInfo;
        private MailAddress mailAddress;
        private MedicalProviderDto medicalProvider;
        private Long consentActivityId;
        private String consentActivityCode;
        private Long consentVersionId;
        private String consentActivityInstanceGuid;
        private String signatureQuestionStableId;
        private String bloodQuestionStableId;
        private String tissueQuestionStableId;
        private String aboutYouActivityInstanceGuid;
        private String dateOfDiagnosisStableId;
        private String dateOfBirthStableId;
        private Long aboutYouActivityId;
        private long enrollmentActionId;
        private long enrollmentEventTriggerId;
        private long consentActivityStatusTriggerId;
        private long enrollmentConfigurationId;

        public GeneratedTestData(Auth0Util.TestingUser testingUser,
                                 UserProfile testUserProfile,
                                 StudyDto study,
                                 StudyClientConfiguration client) {
            this.testUserProfile = testUserProfile;
            this.testingUser = testingUser;
            this.study = study;
            this.client = client;
        }

        public GeneratedTestData() {
        }

        public void mergeTestActivityData(GeneratedTestData otherTestData) {
            this.consentActivityInstanceGuid = otherTestData.consentActivityInstanceGuid;
            this.signatureQuestionStableId = otherTestData.signatureQuestionStableId;
            this.bloodQuestionStableId = otherTestData.bloodQuestionStableId;
            this.tissueQuestionStableId = otherTestData.tissueQuestionStableId;
            this.enrollmentActionId = otherTestData.enrollmentActionId;
            this.enrollmentEventTriggerId = otherTestData.enrollmentEventTriggerId;
            this.consentActivityStatusTriggerId = otherTestData.consentActivityStatusTriggerId;
            this.enrollmentConfigurationId = otherTestData.enrollmentConfigurationId;
            this.dateOfDiagnosisStableId = otherTestData.dateOfDiagnosisStableId;
            this.aboutYouActivityInstanceGuid = otherTestData.aboutYouActivityInstanceGuid;
            this.aboutYouActivityId = otherTestData.aboutYouActivityId;
            this.dateOfBirthStableId = otherTestData.dateOfBirthStableId;
            this.consentActivityCode = otherTestData.consentActivityCode;
        }

        public Auth0Util.TestingUser getTestingUser() {
            return testingUser;
        }

        public StudyClientConfiguration getTestingClient() {
            return client;
        }

        public StudyDto getTestingStudy() {
            return study;
        }

        public void setTestingStudy(StudyDto study) {
            this.study = study;
        }

        public long getUserId() {
            return testingUser.getUserId();
        }

        public String getUserGuid() {
            return testingUser.getUserGuid();
        }

        public long getStudyId() {
            return study.getId();
        }

        public UserProfile getProfile() {
            return testUserProfile;
        }

        public void setTestUserProfile(UserProfile testUserProfile) {
            this.testUserProfile = testUserProfile;
        }

        public String getStudyGuid() {
            return study.getGuid();
        }

        public long getClientId() {
            return client.getClientId();
        }

        public String getAuth0ClientId() {
            return client.getAuth0ClientId();
        }

        public PdfConfigInfo getPdfConfigInfo() {
            return pdfConfigInfo;
        }

        public void setPdfConfigInfo(PdfConfigInfo pdfConfigInfo) {
            this.pdfConfigInfo = pdfConfigInfo;
        }

        public MailAddress getMailAddress() {
            return mailAddress;
        }

        public void setMailAddress(MailAddress mailAddress) {
            this.mailAddress = mailAddress;
        }

        public MedicalProviderDto getMedicalProvider() {
            return medicalProvider;
        }

        public void setMedicalProvider(MedicalProviderDto medicalProvider) {
            this.medicalProvider = medicalProvider;
        }

        public String getUserHruid() {
            return testingUser.getUserHruid();
        }

        public String getConsentActivityInstanceGuid() {
            return consentActivityInstanceGuid;
        }

        public void setConsentActivityInstanceGuid(String consentActivityInstanceGuid) {
            this.consentActivityInstanceGuid = consentActivityInstanceGuid;
        }

        public Long getConsentActivityId() {
            return consentActivityId;
        }

        public void setConsentActivityId(Long consentActivityId) {
            this.consentActivityId = consentActivityId;
        }

        public String getConsentActivityCode() {
            return consentActivityCode;
        }

        public void setConsentActivityCode(String consentActivityCode) {
            this.consentActivityCode = consentActivityCode;
        }

        public Long getConsentVersionId() {
            return consentVersionId;
        }

        public void setConsentVersionId(Long consentVersionId) {
            this.consentVersionId = consentVersionId;
        }

        public long getEnrollmentActionId() {
            return enrollmentActionId;
        }

        public void setEnrollmentActionId(long enrollmentActionId) {
            this.enrollmentActionId = enrollmentActionId;
        }

        public long getEnrollmentEventTriggerId() {
            return enrollmentEventTriggerId;
        }

        public void setEnrollmentEventTriggerId(long enrollmentEventTriggerId) {
            this.enrollmentEventTriggerId = enrollmentEventTriggerId;
        }

        public long getConsentActivityStatusTriggerId() {
            return consentActivityStatusTriggerId;
        }

        public void setConsentActivityStatusTriggerId(long consentActivityStatusTriggerId) {
            this.consentActivityStatusTriggerId = consentActivityStatusTriggerId;
        }

        public long getEnrollmentConfigurationId() {
            return enrollmentConfigurationId;
        }

        public void setEnrollmentConfigurationId(long enrollmentConfigurationId) {
            this.enrollmentConfigurationId = enrollmentConfigurationId;
        }

        public String getSignatureQuestionStableId() {
            return signatureQuestionStableId;
        }

        public void setSignatureQuestionStableId(String signatureQuestionStableId) {
            this.signatureQuestionStableId = signatureQuestionStableId;
        }

        public String getBloodQuestionStableId() {
            return bloodQuestionStableId;
        }

        public void setBloodQuestionStableId(String bloodQuestionStableId) {
            this.bloodQuestionStableId = bloodQuestionStableId;
        }

        public String getTissueQuestionStableId() {
            return tissueQuestionStableId;
        }

        public void setTissueQuestionStableId(String tissueQuestionStableId) {
            this.tissueQuestionStableId = tissueQuestionStableId;
        }

        public String getDateOfDiagnosisStableId() {
            return dateOfDiagnosisStableId;
        }

        public void setDateOfDiagnosisStableId(String dateOfDiagnosisStableId) {
            this.dateOfDiagnosisStableId = dateOfDiagnosisStableId;
        }

        public String getAboutYouActivityInstanceGuid() {
            return aboutYouActivityInstanceGuid;
        }

        public void setAboutYouActivityInstanceGuid(String aboutYouActivityInstanceGuid) {
            this.aboutYouActivityInstanceGuid = aboutYouActivityInstanceGuid;
        }

        public Long getAboutYouActivityId() {
            return aboutYouActivityId;
        }

        public void setAboutYouActivityId(Long aboutYouActivityId) {
            this.aboutYouActivityId = aboutYouActivityId;
        }

        public String getDateOfBirthStableId() {
            return dateOfBirthStableId;
        }

        public void setDateOfBirthStableId(String dateOfBirthStableId) {
            this.dateOfBirthStableId = dateOfBirthStableId;
        }
    }
}
