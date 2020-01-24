package org.broadinstitute.ddp.constants;

public class ConfigFile {

    // field that specifies the url to the database
    public static final String DB_URL = "dbUrl";

    // maximum number of db connections
    public static final String NUM_POOLED_CONNECTIONS = "maxConnections";

    // local port on which spark will listen
    public static final String PORT = "port";

    // whether or not to run liquibase at app boot
    public static final String DO_LIQUIBASE = "doLiquibase";

    // Name of the default timezone to be used by system. Does not change VM time zone!
    public static final String DEFAULT_TIMEZONE = "defaultTimezone";

    // config file name used for testing the app with a disposable in-memory database
    public static final String IN_MEMORY_DB_TESTING_CONFIG_FILE = "testing-inmemorydb.conf";

    // base url to use when running tests against a live backend
    public static final String TESTING_BASE_URL = "baseTestUrl";

    // config file that has sql statements
    public static final String SQL_CONFIG_FILE = "sql";

    public static final String SQL_CONF = SQL_CONFIG_FILE + ".conf";

    // query used to find all studies for an umbrella
    public static final String STUDIES_FOR_UMBRELLA_QUERY = "studiesForUmbrella";

    // file system location of where to find fc keys
    public static final String FIRECLOUD_KEYS_DIR_ENV_VAR = "ddp.firecloudKeysDir";
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
    public static final String STUDY_EXPORT_TO_ES_SCHEDULE = "schedules.elasticsearchExport";
    public static final String TEMP_USER_CLEANUP_SCHEDULE = "schedules.tempUserCleanup";

    //database instance names
    public static final String DB_DSM_INSTANCE_ID = "dbDsmInstance";
    public static final String DB_API_INSTANCE_ID = "dbApiInstance";
    public static final String DB_HOUSEKEEPING_INSTANCE_ID = "dbHousekeepingInstance";

    // SQL queries
    public static final String CLIENT_KEY_CONFIGURATION_QUERY = "clientKeyConfigurationQuery";
    public static final String CLIENT_ID_BY_AUTH0_CLIENT_ID_QUERY = "clientIdByAuth0ClientIdQuery";
    public static final String CLIENT_UPSERT_STATEMENT = "upsertClientStatement";
    public static final String CLIENT_DELETE_BY_NAME = "deleteClientByName";
    public static final String CHECK_USER_GUID_QUERY = "checkUserGuidQuery";
    public static final String INSERT_USER_STMT = "insertUser";
    public static final String UPSERT_USER_PROFILE_FIRST_AND_LAST_NAME = "upsertFirstAndLastName";
    public static final String INSERT_GOVERNED_PARTICIPANT = "insertGovernedUserStmt";
    public static final String STUDIES_FOR_CLIENT_QUERY = "studiesForClientQuery";
    public static final String QUERY_GOVERNED_PARTICIPANTS_BY_OPERATOR = "governedParticipantsForOperatorQuery";
    public static final String USER_EXISTS_QUERY = "userExistsQuery";
    public static final String PROFILE_EXISTS_QUERY = "profileExistsQuery";
    public static final String GET_USER_ID_FROM_GUID = "getUserIdFromGuidQuery";
    public static final String GET_USER_ID_FROM_HRUID = "getUserIdFromHruidQuery";
    public static final String USER_EXISTS_GUID = "userExistsGuidQuery";
    public static final String GOVERNANCE_ALIAS_EXISTS_QUERY = "governanceAliasExistsQuery";
    public static final String GET_ALL_GOV_PARTS_QUERY = "governedParticipantsForOperatorQuery";
    public static final String PATCH_SEX_STMT = "patchSexStmt";
    public static final String PATCH_PREFERRED_LANGUAGE_STMT = "patchPreferredLanguageStmt";
    public static final String USER_GUID_FOR_AUTH0ID_QUERY = "userGuidForAuth0IdQuery";
    public static final String HEALTHCHECK_PASSWORD = "healthcheckPassword";
    public static final String USER_CLIENT_REVOCATION_QUERY = "userClientRevocationQuery";
    public static final String GET_TEMPLATE_VARIABLES_WITH_TRANSLATIONS_QUERY =
            "templateVariablesWithTranslationsQuery";

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

    public static final String BASE_WEBDRIVER_URL = "baseWebDriverUrl";
    public static final String FIRECLOUD = "firecloud";
    public static final String TEST_FIRECLOUD_WORKSPACE_NAMESPACE = "testWorkspaceNamespace";
    public static final String TEST_FIRECLOUD_WORKSPACE_NAME = "testWorkspace";
    public static final String SENDGRID_API_KEY = "sendgridToken";
    public static final String HOUSEKEEPING_DB_URL = "housekeepingDbUrl";
    public static final String HOUSEKEEPING_NUM_POOLED_CONNECTIONS = "housekeepingMaxConnections";
    public static final String BACKEND_AUTH0_TEST_CLIENT_ID = "backendTestClientId";
    public static final String BACKEND_AUTH0_TEST_SECRET = "backendTestSecret";
    public static final String BACKEND_AUTH0_TEST_CLIENT_NAME = "backendTestClientName";
    public static final String BACKEND_AUTH0_TEST_CLIENT_ID2 = "backendTestClientId2";
    public static final String BACKEND_AUTH0_TEST_SECRET2 = "backendTestSecret2";
    public static final String BACKEND_AUTH0_TEST_CLIENT_NAME2 = "backendTestClientName2";
    public static final String PUBSUB_PROJECT = "pubSubProject";
    public static final String SLACK_HOOK = "slack.hook";
    public static final String SLACK_CHANNEL = "slack.channel";
    public static final String TEST_USER_AUTH0_ID = "testUserAuth0Id";

    public static final String DOMAIN = "domain";
    public static final String SEND_METRICS = "sendMetrics";

    /**
     * Google API key for geocoding
     */
    public static final String GEOCODING_API_KEY = "googleGeocodingApiKey";

    public static final String ELASTICSEARCH_URL = "elasticsearchUrl";
    public static final String ELASTICSEARCH_PASSWORD = "elasticsearchPassword";
    public static final String ELASTICSEARCH_USERNAME = "elasticsearchUsername";
    public static final String ELASTICSEARCH_EXPORT_BATCH_SIZE = "elasticsearchBatchSize";

    public static final String DSM_BASE_URL = "dsmBaseUrl";
    public static final String DSM_JWT_SECRET = "dsmJwtSecret";
    public static final String USE_DISPOSABLE_TEST_DB = "useDisposableTestDbs";

    public static final class QuestionQueries {
        public static final String BOOL_INFO_BY_QUESTION_ID_QUERY = "questions.boolInfoByQuestionIdQuery";
        public static final String TEXT_INFO_BY_QUESTION_ID_QUERY = "questions.textInfoByQuestionIdQuery";
    }

    public static final class SqlQuery {
        public static final String ACTIVITY_INSTANCE_GUID_AND_PARTICIPANT_ID_BY_STUDY_GUID =
                "studyActivity.queryActivityInstanceGuidAndParticipantId";
        public static final String VALID_STUDY_QUERY = "studyActivity.queryValidStudy";

        public static final String FORM_ACTIVITY_BY_GUID = "activities.formActivityByGuidQuery";

        public static final String ANSWERS_FOR_QUESTION = "answers.queryAllByFormGuidAndQuestionStableId";
        public static final String ANSWER_GUIDS_FOR_QUESTION = "answers.queryGuidsByFormGuidAndQuestionStableId";
        public static final String BOOL_ANSWER_BY_ID = "answers.queryBoolAnswerById";
        public static final String TEXT_ANSWER_BY_ID = "answers.queryTextAnswerById";
        public static final String ANSWER_ID_BY_GUIDS = "answers.queryAnswerIdByGuids";

        public static final String VALIDATIONS_FOR_QUESTION = "validations.queryAllByQuestionAndLangId";

        public static final String USER_GUID_BY_ID = "getUserGuidFromUserIdQuery";
        public static final String MIN_AND_MAX_LENGTH_VALIDATION = "validations.minAndMaxLengthValidationQuery";
        public static final String REGEX_PATTERN_VALIDATION = "validations.regexPatternValidationQuery";
        public static final String NUM_OPTIONS_SELECTED_VALIDATION = "validations.numOptionsSelectedValidationQuery";

        public static final String MAX_INSTANCES_PER_USER = "max_instances_per_user";
        public static final String NUM_INSTANCES_FOR_USER = "num_instances_for_user";
        public static final String PEX_PRECONDITION = "pex_precondition";
        public static final String PEX_CANCEL_CONDITION = "pex_cancel_condition";

        public static final String PARTICIPANT_GUID = "participant_guid";
        public static final String PARTICIPANT_HRUID = "participant_hruid";

        // needed to disambiguate cases where there are multiple different guids in a resulset
        public static final String STUDY_GUID = "study_guid";
    }

    public static final class SqlStmt {
        public static final String CREATE_ANSWER = "answers.createAnswerStmt";
        public static final String CREATE_BOOL_ANSWER = "answers.createBoolAnswerStmt";
        public static final String CREATE_TEXT_ANSWER = "answers.createTextAnswerStmt";
        public static final String UPDATE_ANSWER_BY_ID = "answers.updateAnswerByIdStmt";
        public static final String UPDATE_BOOL_ANSWER_BY_ID = "answers.updateBoolAnswerByIdStmt";
        public static final String UPDATE_TEXT_ANSWER_BY_ID = "answers.updateTextAnswerByIdStmt";
        public static final String DELETE_ANSWER_BY_ID = "answers.deleteAnswerByIdStmt";
        public static final String DELETE_BOOL_ANSWER_BY_ID = "answers.deleteBoolAnswerByIdStmt";
        public static final String DELETE_TEXT_ANSWER_BY_ID = "answers.deleteTextAnswerByIdStmt";
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

    public static class Sendgrid {
        public static final String TEMPLATES = "sendgridTemplates";
        public static final String TEMPLATE = "template";
        public static final String TEMPLATE_VERSION = "version";
        public static final String FROM_NAME = "sendgrid.fromName";
        public static final String FROM_EMAIL = "sendgrid.fromEmail";
    }
}
