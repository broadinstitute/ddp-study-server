package org.broadinstitute.ddp.constants;

public class ConfigFile {

    public static final String API_BASE_URL = "apiBaseUrl";

    // field that specifies the url to the database
    public static final String DB_URL = "dbUrl";

    // maximum number of db connections
    public static final String NUM_POOLED_CONNECTIONS = "maxConnections";

    // local port on which spark will listen
    public static final String PORT = "port";

    // whether or not to run liquibase at app boot
    public static final String DO_LIQUIBASE = "doLiquibase";
    public static final String DO_LIQUIBASE_IN_STUDY_SERVER = "doLiquibaseInStudyServer";

    // Name of the default timezone to be used by system. Does not change VM time zone!
    public static final String DEFAULT_TIMEZONE = "defaultTimezone";

    // config file name used for testing the app with a disposable in-memory database
    public static final String IN_MEMORY_DB_TESTING_CONFIG_FILE = "testing-inmemorydb.conf";

    // base url to use when running tests against a live backend
    public static final String TESTING_BASE_URL = "baseTestUrl";

    // config file that has sql statements
    public static final String SQL_CONFIG_FILE = "sql";

    public static final String SQL_CONF = SQL_CONFIG_FILE + ".conf";

    // file system location of where to find fc keys
    public static final String ITEXT_FILE_ENV_VAR = "itext.license";

    // encryption secret for database saved auth0 secrets
    public static final String ENCRYPTION_SECRET = "encryptionSecret";

    // whether to run the housekeeping scheduler or not
    public static final String RUN_SCHEDULER = "runScheduler";

    // whether to ensure usage of default GCP credentials or not
    public static final String REQUIRE_DEFAULT_GCP_CREDENTIALS = "requireDefaultGcpCredentials";

    // Google project id to use for study export
    public static final String GOOGLE_PROJECT_ID = "googleProjectId";

    // Google Storage bucket name to use for study export
    public static final String STUDY_EXPORT_BUCKET = "studyExportBucket";

    // Google Storage bucket name to use for generated PDFs
    public static final String PDF_ARCHIVE_BUCKET = "pdfArchiveBucket";

    // whether to use local filesystem for pdf archiving or not
    public static final String PDF_ARCHIVE_USE_FILESYSTEM = "pdfArchiveUseFilesystem";

    // the cron schedule for different jobs
    public static final String CHECK_AGE_UP_SCHEDULE = "schedules.checkAgeUp";
    public static final String DRUG_LOADER_SCHEDULE = "schedules.drugLoader";
    public static final String CANCER_LOADER_SCHEDULE = "schedules.cancerLoader";
    public static final String DB_BACKUP_SCHEDULE = "schedules.dbBackup";
    public static final String DB_BACKUP_CHECK_SCHEDULE = "schedules.dbBackupCheck";
    public static final String STUDY_EXPORT_SCHEDULE = "schedules.studyExport";
    public static final String TEMP_USER_CLEANUP_SCHEDULE = "schedules.tempUserCleanup";

    // database instance names
    public static final String DB_INSTANCE_ID = "dbInstanceId";

    // SQL queries
    public static final String HEALTHCHECK_PASSWORD = "healthcheckPassword";

    // milliseconds after which spark will terminate a request thread if it hasn't completed
    public static final String THREAD_TIMEOUT = "threadTimeout";
    public static final String AUTH0 = "auth0";

    // controls whether the app server is booted in a separate process (when true) or in-process
    // in a separate thread (when false).  For local testing, booting in-process means you can
    // set breakpoints in both the test code and the app code, but the downside is that since
    // both test code and app server are in the same JVM, behavior may not represent real-world
    // behavior.  For a more realistic environment--but more hassle with remote debugging--
    // you can boot the app in a separate process.
    public static final String BOOT_TEST_APP_IN_SEPARATE_PROCESS = "bootTestAppInSeparateProcess";

    public static final String AUTH0_DSM_CLIENT_ID = "dsmClientId";
    public static final String AUTH0_DSM_CLIENT_SECRET = "dsmClientSecret";
    public static final String AUTH0_DSM_API_AUDIENCE = "dsmApiAudience";

    public static final String EASY_POST_API_KEY = "easyPostApiKey";
    public static final String USE_PUBSUB_EMULATOR = "usePubSubEmulator";

    public static final String SENDGRID_API_KEY = "sendgridToken";
    public static final String HOUSEKEEPING_DB_URL = "housekeepingDbUrl";
    public static final String HOUSEKEEPING_NUM_POOLED_CONNECTIONS = "housekeepingMaxConnections";
    public static final String BACKEND_AUTH0_TEST_CLIENT_ID = "backendTestClientId";
    public static final String BACKEND_AUTH0_TEST_SECRET = "backendTestSecret";
    public static final String BACKEND_AUTH0_TEST_CLIENT_NAME = "backendTestClientName";
    public static final String BACKEND_AUTH0_TEST_CLIENT_ID2 = "backendTestClientId2";
    public static final String BACKEND_AUTH0_TEST_SECRET2 = "backendTestSecret2";
    public static final String BACKEND_AUTH0_TEST_CLIENT_NAME2 = "backendTestClientName2";
    public static final String PUBSUB_ENABLE_HKEEP_TASKS = "pubsub.enableHousekeepingTasks";
    public static final String PUBSUB_HKEEP_TASKS_SUB = "pubsub.housekeepingTasksSubscription";
    public static final String SLACK_HOOK = "slack.hook";
    public static final String SLACK_CHANNEL = "slack.channel";
    public static final String SLACK_QUEUE_SIZE = "slack.queueSize";
    public static final String SLACK_INTERVAL_IN_MILLIS = "slack.intervalInMillis";
    public static final String TEST_USER_AUTH0_ID = "testUserAuth0Id";

    public static final String DOMAIN = "domain";
    public static final String SEND_METRICS = "sendMetrics";

    public static final String RESTRICT_REGISTER_ROUTE = "restrictRegisterRoute";

    /**
     * Google API key for geocoding
     */
    public static final String GEOCODING_API_KEY = "googleGeocodingApiKey";

    public static final String ELASTICSEARCH_URL = "elasticsearchUrl";
    public static final String ELASTICSEARCH_PASSWORD = "elasticsearchPassword";
    public static final String ELASTICSEARCH_USERNAME = "elasticsearchUsername";
    public static final String ELASTICSEARCH_EXPORT_BATCH_SIZE = "elasticsearchBatchSize";
    public static final String ELASTICSEARCH_PROXY = "elasticsearchProxy";

    public static final String DSM_BASE_URL = "dsmBaseUrl";
    public static final String DSM_JWT_SECRET = "dsmJwtSecret";
    public static final String DSM_JWT_SIGNER = "dsmJwtSigner";
    public static final String USE_DISPOSABLE_TEST_DB = "useDisposableTestDbs";

    public static final String AUTH0_IP_WHITE_LIST = "auth0IpWhiteList";
    public static final String PREFERRED_SOURCE_IP_HEADER = "preferredSourceIPHeader";

    public static final String JCACHE_CONFIGURATION_FILE = "jcacheConfigurationFile";
    public static final String REDIS_SERVER_ADDRESS = "redisServerAddress";

    public static final class SqlQuery {
        public static final String FORM_ACTIVITY_BY_GUID = "activities.formActivityByGuidQuery";

        public static final String MAX_INSTANCES_PER_USER = "max_instances_per_user";
        public static final String NUM_INSTANCES_FOR_USER = "num_instances_for_user";
    }

    public static final class API_RATE_LIMIT {
        public static final String MAX_QUERIES_PER_SECOND = "rateLimit.apiLimitRate";
        public static final String BURST = "rateLimit.apiLimitBurst";
    }

    /**
     * For production code, all auth0 settings go into the database.  For
     * testing, we can keep things in the config file to keep life simpler and
     * migrate into the db at a later time.
     */
    public static class Auth0Testing {
        public static final String AUTH0_CLIENT_ID = "clientId";
        public static final String AUTH0_SECRET = "clientSecret";
        public static final String AUTH0_TEST_EMAIL = "testUser";
        public static final String AUTH0_TEST_USER_GUID = "testUserGuid";
        public static final String AUTH0_TEST_PASSWORD = "testUserPassword";
        public static final String AUTH0_TEST_ADMIN_EMAIL = "testAdmin";
        public static final String AUTH0_TEST_ADMIN_PASSWORD = "testAdminPassword";
        public static final String AUTH0_CLIENT_NAME = "clientName";
        public static final String AUTH0_TEST_USER_AUTH0_ID = "testUserAuth0Id";
        public static final String AUTH0_ADMIN_TEST_USER_AUTH0_ID = "testAdminUserAuth0Id";
        public static final String AUTH0_CLIENT_SECRET = "clientSecret";
        public static final String AUTH0_MGMT_API_CLIENT_ID = "managementApiClientId";
        public static final String AUTH0_MGMT_API_CLIENT_SECRET = "managementApiSecret";
        public static final String AUTH0_MGMT_API_CLIENT_ID2 = "managementApiClientId2";
        public static final String AUTH0_MGMT_API_CLIENT_SECRET2 = "managementApiSecret2";
        public static final String AUTH0_DOMAIN2 = "domain2";
    }

    public static class Elasticsearch {
        public static final String SYNC_ENABLED = "elasticsearch.syncEnabled";
        public static final String SYNC_INTERVAL_SECS = "elasticsearch.syncIntervalSecs";
    }

    public static class Kits {
        public static final String CHECK_ENABLED = "kits.checkEnabled";
        public static final String INTERVAL_SECS = "kits.intervalSecs";
        public static final String STATUS_CHECK_SECS = "kits.statusCheckSecs";
        public static final String BATCH_SIZE = "kits.batchSize";
    }

    public static class Sendgrid {
        public static final String TEMPLATES = "sendgridTemplates";
        public static final String TEMPLATE = "template";
        public static final String TEMPLATE_VERSION = "version";
        public static final String FROM_NAME = "sendgrid.fromName";
        public static final String FROM_EMAIL = "sendgrid.fromEmail";
        // The proxy URL to use for all outgoing SendGrid requests.
        public static final String PROXY = "sendgrid.proxy";
    }
}
