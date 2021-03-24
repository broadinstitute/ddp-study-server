package org.broadinstitute.dsm.statics;

public class ApplicationConfigConstants {

    //ddp configuration
    public static final String DDP = "ddp";
    public static final String INSTANCE_NAME = "instanceName";
    public static final String TOKEN_SECRET = "tokenSecret";
    public static final String EASYPOST_API_KEY = "easyPostApiKey";

    //external shipper configuration
    public static final String EXTERNAL_SHIPPER = "externalShipper";
    public static final String SHIPPER_NAME = "shipperName";
    public static final String API_KEY = "apiKey";
    public static final String BASE_URL = "baseUrl";
    public static final String CLASS_NAME = "className";
    public static final String TEST = "test";

    //db connection information
    public static final String DSM_DB_MAX_CONNECTIONS = "portal.maxConnections";
    public static final String DSM_DB_URL = "portal.dbUrl";

    //google buckets
    public static final String GOOGLE_PROJECT_NAME = "portal.googleProjectName";
    public static final String GOOGLE_DISCARD_BUCKET = "portal.discardSampleBucket";
    public static final String GOOGLE_CONFIG_BUCKET = "portal.configBucket";
    public static final String GOOGLE_CREDENTIALS = "portal.googleProjectCredentials";

    //ES
    public static final String ES_URL = "elasticSearch.url";
    public static final String ES_USERNAME = "elasticSearch.username";
    public static final String ES_PASSWORD = "elasticSearch.password";
    public static final String ES_PROXY = "elasticSearch.proxy";

    //security information
    public static final String BSP_SECRET = "bsp.secret";
    public static final String BROWSER_JWT_SECRET = "browser_security.jwt_secret";
    public static final String BROWSER_COOKIE_SALT = "browser_security.cookie_salt";
    public static final String BROWSER_COOKIE_NAME = "browser_security.cookie_name";
    public static final String INTERNAL_API_SECRET = "internal_api.secret";

    //auth0
    public static final String AUTH0_SECRET = "auth0.secret";
    public static final String AUTH0_ACCOUNT = "auth0.account";
    public static final String AUTH0_CLIENT_KEY = "auth0.clientKey";
    public static final String AUTH0_CONNECTIONS = "auth0.connections";
    public static final String AUTH0_IS_BASE_64_ENCODED = "auth0.isSecretBase64Encoded";
    public static final String AUTH0_MGT_SECRET = "auth0.mgtSecret";
    public static final String AUTH0_MGT_API_URL = "auth0.mgtApiUrl";
    public static final String AUTH0_MGT_KEY = "auth0.mgtKey";
    public static final String AUTH0_DOMAIN = "auth0.domain";
    public static final String AUTH0_AUDIENCE = "auth0.audience";

    //quartz
    public static final String QUARTZ_ENABLE_JOBS = "quartz.enableJobs";
    public static final String QUARTZ_DDP_REQUEST_JOB_INTERVAL_SEC = "quartz.ddpRequestJob_jobIntervalInSeconds";
    public static final String QUARTZ_NOTIFICATION_JOB_INTERVAL_SEC = "quartz.notificationJob_jobIntervalInSeconds";
    public static final String QUARTZ_LABEL_CREATION_JOB_INTERVAL_SEC = "quartz.labelCreationJob_jobIntervalInSeconds";
    public static final String QUARTZ_CRON_EXPRESSION_FOR_DDP_EVENT_TRIGGER = "quartz.ddp_event_trigger_cron_expression";
    public static final String QUARTZ_CRON_EXPRESSION_FOR_EXTERNAL_SHIPPER = "quartz.externalShipper_cron_expression";
    public static final String QUARTZ_CRON_EXPRESSION_FOR_EXTERNAL_SHIPPER_ADDITIONAL = "quartz.externalShipper_cron_expression_additional";
    public static final String QUARTZ_CRON_STATUS_SHIPMENT = "quartz.shipmentStatusJob_cron_expression";
    public static final String QUARTZ_UPS_LOOKUP_JOB = "quartz.ups_lookup_cron_expression";

    //email
    public static final String EMAIL_CRON_EXPRESSION_FOR_GP_NOTIFICATION = "email.cron_expression_GP_notification";
    public static final String EMAIL_FRONTEND_URL_FOR_LINKS = "email.frontendUrl";
    public static final String EMAIL_GP_RECIPIENT = "email.gp_recipient";
    public static final String EMAIL_CLASS_NAME = "email.className";
    public static final String EMAIL_KEY = "email.key";
    public static final String EMAIL_CLIENT_SETTINGS = "email.clientSettings";
    public static final String EMAIL_NOTIFICATIONS = "email.notifications";
    public static final String EMAIL_REMINDER_NOTIFICATIONS = "email.reminderNotifications";
    public static final String EMAIL_NOTIFICATION_REASON = "reason";
    public static final String EMAIL_REMINDER_NOTIFICATIONS_REMINDERS = "reminders";
    public static final String EMAIL_NOTIFICATIONS_SEND_GRID_TEMPLATE_ID = "sendGridTemplate";

    //db queries
    public static final String GET_BSP_RESPONSE_INFORMATION_FOR_KIT = "portal.kit_query";
    public static final String GET_RECEIVED_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL = "portal.selectReceivedKitForNotification";
    public static final String GET_SENT_KIT_INFORMATION_FOR_NOTIFICATION_EMAIL = "portal.selectSentKitForNotification";
    public static final String GET_ALLOWED_REALMS_FOR_USER_ROLE_STARTS_LIKE = "portal.selectAllowedRealmsStartsLike";
    public static final String GET_ROLES_LIKE = "portal.getRoles";
    public static final String UPDATE_KIT_REQUEST = "portal.updateKitRequest";
    public static final String INSERT_KIT_TRACKING = "portal.insertKitTrackingRequest";
    public static final String UPDATE_KIT_ERROR = "portal.updateKitRequestError";
    public static final String GET_DDP_PARTICIPANT_ID = "portal.getDDPParticipantId";
    public static final String GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS = "portal.dashboardKitRequests";
    public static final String GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS_DEACTIVATED = "portal.dashboardKitDeactivated";
    public static final String GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS_SENT_REPORT = "portal.dashboardReportKitSent";
    public static final String GET_DASHBOARD_INFORMATION_OF_KIT_REQUESTS_RECEIVED_REPORT = "portal.dashboardReportKitReceived";
    public static final String GET_FOUND_IF_KIT_WITH_DDP_LABEL_ALREADY_EXISTS = "portal.checkDdpLabelIdQuery";
    public static final String GET_FOUND_IF_KIT_LABEL_ALREADY_EXISTS_IN_TRACKING_TABLE = "portal.checkTrackingQuery";
    public static final String GET_KIT_TYPE_NEED_TRACKING_BY_DDP_LABEL = "portal.checkKitTypeNeedsTrackingQuery";
    public static final String INSERT_KIT_REQUEST = "portal.insertKitRequest";
    public static final String INSERT_KIT = "portal.insertKit";
    public static final String GET_COUNT_KITS_WITH_SAME_COLLABORATOR_SAMPLE_ID_AND_KIT_TYPE = "portal.counterCollaboratorSample";
    public static final String GET_UNSENT_KIT_REQUESTS_FOR_REALM = "portal.unsentKitRequestsPerRealmQuery";
    public static final String GET_UPLOADED_KITS = "portal.selectUploadedKits";
    public static final String GET_LABEL_SETTINGS = "portal.labelSettingsQuery";
    public static final String UPDATE_LABEL_SETTINGS = "portal.updateLabelSettings";
    public static final String INSERT_LABEL_SETTINGS = "portal.insertLabelSettings";

    public static final String GET_PARTICIPANT_EVENTS = "portal.selectParticipantEvents";
    public static final String GET_PARTICIPANT_EVENT = "portal.selectParticipantEvent";
    public static final String INSERT_PARTICIPANT_EVENT = "portal.insertParticipantEvent";
    public static final String INSERT_SURVEY_TRIGGER = "portal.insertSurveyTrigger";
    public static final String GET_KIT_OF_EXITED_PARTICIPANTS = "portal.exitedKits";
    public static final String UPDATE_KIT_DISCARD_ACTION = "portal.exitedKitAction";
    public static final String UPDATE_KIT_DISCARDED = "portal.exitedKitDiscarded";
    public static final String INSERT_KIT_DISCARD = "portal.insertKitDiscard";
    public static final String UPDATE_KIT_DISCARD = "portal.updateKitDiscard";
    public static final String SET_USER_CONFIRMED = "portal.userConfirmed";

    public static final String PREFERRED_SOURCE_IP_HEADER = "preferredSourceIPHeader";
    public static final String BOOT_TIMEOUT = "bootTimeout";

    public static final String CORS_ALLOWED_ORIGINS = "corsOrigins";

    public static final String CARE_EVOLVE_ACCOUNT ="careEvolve.account";

    public static final String CARE_EVOLVE_SUBSCRIBER_KEY = "careEvolve.subscriberKey";

    public static final String CARE_EVOLVE_SERVICE_KEY = "careEvolve.serviceKey";

    public static final String CARE_EVOLVE_ORDER_ENDPOINT = "careEvolve.orderEndpoint";
    public static final String CARE_EVOLVE_PROVIDER_FIRSTNAME = "careEvolve.provider.firstName";
    public static final String CARE_EVOLVE_PROVIDER_LAST_NAME = "careEvolve.provider.lastName";
    public static final String CARE_EVOLVE_PROVIDER_NPI = "careEvolve.provider.NPI";
    public static final String CARE_EVOLVE_MAX_RETRIES = "careEvolve.maxRetries";
    public static final String CARE_EVOLVE_RETRY_WAIT_SECONDS = "careEvolve.retryWaitSeconds";
}
