package org.broadinstitute.dsm;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.net.MediaType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.BasicServer;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.security.Auth0Util;
import org.broadinstitute.ddp.security.CookieUtil;
import org.broadinstitute.ddp.util.BasicTriggerListener;
import org.broadinstitute.ddp.util.JsonTransformer;
import org.broadinstitute.ddp.util.Utility;
import org.broadinstitute.dsm.careevolve.Provider;
import org.broadinstitute.dsm.jetty.JettyConfig;
import org.broadinstitute.dsm.jobs.*;
import org.broadinstitute.dsm.log.SlackAppender;
import org.broadinstitute.dsm.pubsub.DSMtasksSubscription;
import org.broadinstitute.dsm.pubsub.PubSubResultMessageSubscription;
import org.broadinstitute.dsm.route.*;
import org.broadinstitute.dsm.route.familymember.AddFamilyMemberRoute;
import org.broadinstitute.dsm.route.participant.GetParticipantDataRoute;
import org.broadinstitute.dsm.route.participant.GetParticipantRoute;
import org.broadinstitute.dsm.security.JWTConverter;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.*;
import org.broadinstitute.dsm.util.externalShipper.GBFRequestUtil;
import org.broadinstitute.dsm.util.triggerListener.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.KeyMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static spark.Spark.*;

public class DSMServer extends BasicServer {

    private static final Logger logger = LoggerFactory.getLogger(DSMServer.class);

    private static final String API_ROOT = "/ddp/";
    private static final String UI_ROOT = "/ui/";

    private static final String[] CORS_HTTP_METHODS = new String[] { "GET", "PUT", "POST", "OPTIONS", "PATCH" };
    private static final String[] CORS_HTTP_HEADERS = new String[] { "Content-Type", "Authorization", "X-Requested-With",
            "Content-Length", "Accept", "Origin", "" };

    public static final String CONFIG = "config";
    public static final String NOTIFICATION_UTIL = "NotificationUtil";
    public static final String KIT_UTIL = "KitUtil";
    public static final String DDP_UTIL = "DDPRequestUtil";
    public static final String EVENT_UTIL = "EventUtil";
    public static final String ADDITIONAL_CRON_EXPRESSION = "externalShipper_cron_expression_additional";
    public static final String GCP_PATH_TO_SERVICE_ACCOUNT = "portal.googleProjectCredentials";
    public static final String UPS_PATH_TO_USERNAME = "ups.username";
    public static final String UPS_PATH_TO_PASSWORD = "ups.password";
    public static final String UPS_PATH_TO_ACCESSKEY = "ups.accesskey";
    public static final String UPS_PATH_TO_ENDPOINT = "ups.url";

    public static Provider provider;
    public static final String GCP_PATH_TO_PUBSUB_PROJECT_ID = "pubsub.projectId";
    public static final String GCP_PATH_TO_PUBSUB_SUB = "pubsub.subscription";
    public static final String GCP_PATH_TO_DSS_TO_DSM_SUB = "pubsub.dss_to_dsm_subscription";
    public static final String GCP_PATH_TO_DSM_TO_DSS_TOPIC = "pubsub.dsm_to_dss_topic";
    public static final String GCP_PATH_TO_DSM_TASKS_SUB = "pubsub.dsm_tasks_subscription";

    private static Map<String, JsonElement> ddpConfigurationLookup = new HashMap<>();
    private static final String VAULT_DOT_CONF = "vault.conf";
    private static final String GAE_DEPLOY_DIR = "appengine/deploy";
    private static AtomicBoolean isReady = new AtomicBoolean(false);
    private static final Duration DEFAULT_BOOT_WAIT = Duration.ofMinutes(10);

    private static Auth0Util auth0Util;

    public static void main(String[] args) {
        // immediately lock isReady so that ah/start route will wait
        synchronized (isReady) {
            logger.info("Starting up DSM");
            //config without secrets
            Config cfg = ConfigFactory.load();
            //secrets from vault in a config file
            File vaultConfigInCwd = new File(VAULT_DOT_CONF);
            File vaultConfigInDeployDir = new File(GAE_DEPLOY_DIR, VAULT_DOT_CONF);
            File vaultConfig = vaultConfigInCwd.exists() ? vaultConfigInCwd : vaultConfigInDeployDir;
            logger.info("Reading config values from " + vaultConfig.getAbsolutePath());
            cfg = cfg.withFallback(ConfigFactory.parseFile(vaultConfig));

            if (cfg.hasPath(GCP_PATH_TO_SERVICE_ACCOUNT)) {
                if (StringUtils.isNotBlank(cfg.getString("portal.googleProjectCredentials"))) {
                    System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", cfg.getString("portal.googleProjectCredentials"));
                }
            }

            String preferredSourceIPHeader = null;
            if (cfg.hasPath(ApplicationConfigConstants.PREFERRED_SOURCE_IP_HEADER)) {
                preferredSourceIPHeader = cfg.getString(ApplicationConfigConstants.PREFERRED_SOURCE_IP_HEADER);
            }
            JettyConfig.setupJetty(preferredSourceIPHeader);
            DSMServer server = new DSMServer();
            server.configureServer(cfg);
            isReady.set(true);
            logger.info("DSM Startup Complete");
        }
    }

    protected void configureServer(@NonNull Config config) {
        logger.info("Property source: " + config.getString("portal.environment"));
        logger.info("Configuring the server...");
        threadPool(-1, -1, 60000);
        int port = config.getInt("portal.port");
        String appEnginePort = System.getenv("PORT");

        // if port is passed via env var, assume it's GAE and prefer this port
        if (appEnginePort != null) {
            port = Integer.parseInt(appEnginePort);
        }
        long bootTimeoutSeconds = DEFAULT_BOOT_WAIT.getSeconds();
        if (config.hasPath(ApplicationConfigConstants.BOOT_TIMEOUT)) {
            bootTimeoutSeconds = config.getInt(ApplicationConfigConstants.BOOT_TIMEOUT);
        }


        logger.info("Using port {}", port);
        port(port);

        registerAppEngineStartupCallback(bootTimeoutSeconds);

        setupDB(config);

        // don't run superclass routing--it won't work with JettyConfig changes for capturing proper IP address in GAE
        setupCustomRouting(config);

        List<String> allowedOrigins = config.getStringList(ApplicationConfigConstants.CORS_ALLOWED_ORIGINS);
        enableCORS(StringUtils.join(allowedOrigins, ","), String.join(",", CORS_HTTP_METHODS), String.join(",", CORS_HTTP_HEADERS));
    }

    protected void setupCustomRouting(@NonNull Config cfg) {
        logger.info("Setup DSM custom routes...");

        //BSP route
        String bspSecret = cfg.getString(ApplicationConfigConstants.BSP_SECRET);

        if (StringUtils.isBlank(bspSecret)) {
            throw new RuntimeException("No secret supplied for BSP endpoint, system exiting.");
        }

        //  capture basic route info for logging
        before("*", new LoggingFilter());
        afterAfter((req, res) -> MDC.clear());

        before(API_ROOT + "*", (req, res) -> {
            if (!new JWTRouteFilter(bspSecret, null).isAccessAllowed(req)) {
                halt(404);
            }
            res.header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        });

        //DSM internal routes
        EventUtil eventUtil = new EventUtil();
        NotificationUtil notificationUtil = new NotificationUtil(cfg);

        setupPubSub(cfg, notificationUtil);

        get(API_ROOT + RoutePath.BSP_KIT_QUERY_PATH, new BSPKitRoute(notificationUtil), new JsonTransformer());
        get(API_ROOT + RoutePath.BSP_KIT_REGISTERED, new BSPKitRegisteredRoute(), new JsonTransformer());
        get(API_ROOT + RoutePath.CLINICAL_KIT_ENDPOINT, new ClinicalKitsRoute(notificationUtil), new JsonTransformer());
        get(API_ROOT + RoutePath.CREATE_CLINICAL_KIT_ENDPOINT, new CreateClinicalDummyKitRoute(), new JsonTransformer());

        if(!cfg.getBoolean("ui.production")){
            get(API_ROOT + RoutePath.DUMMY_ENDPOINT, new CreateBSPDummyKitRoute(), new JsonTransformer());
        }

        String appRoute = cfg.hasPath("portal.appRoute") ? cfg.getString("portal.appRoute") : null;

        if (StringUtils.isBlank(appRoute)) {
            throw new RuntimeException("appRoute was not configured correctly.");
        }

        String jwtSecret = cfg.getString(ApplicationConfigConstants.BROWSER_JWT_SECRET);
        String cookieSalt = cfg.getString(ApplicationConfigConstants.BROWSER_COOKIE_SALT);
        String cookieName = cfg.getString(ApplicationConfigConstants.BROWSER_COOKIE_NAME);
        new SecurityUtil(jwtSecret);

        // path is: /app/drugs (this gets the list of display names)
        DrugRoute drugRoute = new DrugRoute();
        get(appRoute + RoutePath.DRUG_LIST_REQUEST, drugRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.DRUG_LIST_REQUEST, drugRoute, new JsonTransformer());

        CancerRoute cancerRoute = new CancerRoute();
        get(appRoute + RoutePath.CANCER_LIST_REQUEST, cancerRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.CANCER_LIST_REQUEST, cancerRoute, new JsonTransformer());

        UserUtil userUtil = new UserUtil();

        auth0Util = new Auth0Util(cfg.getString(ApplicationConfigConstants.AUTH0_ACCOUNT),
                cfg.getStringList(ApplicationConfigConstants.AUTH0_CONNECTIONS),
                cfg.getBoolean(ApplicationConfigConstants.AUTH0_IS_BASE_64_ENCODED),
                cfg.getString(ApplicationConfigConstants.AUTH0_CLIENT_KEY),
                cfg.getString(ApplicationConfigConstants.AUTH0_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_KEY),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_API_URL),
                false, cfg.getString(ApplicationConfigConstants.AUTH0_AUDIENCE));

        before("/info/" + RoutePath.PARTICIPANT_STATUS_REQUEST, (req, res) -> {
            String tokenFromHeader = Utility.getTokenFromHeader(req);
            DecodedJWT validToken = JWTConverter.verifyDDPToken(tokenFromHeader, cfg.getString(ApplicationConfigConstants.AUTH0_DOMAIN));
            if (validToken == null) {
                logger.error(req.pathInfo() + " was called without valid token");
                halt(401, SecurityUtil.ResultType.AUTHENTICATION_ERROR.toString());
            }
        });

        get("/info/" + RoutePath.PARTICIPANT_STATUS_REQUEST, new ParticipantStatusRoute(), new JsonNullTransformer());


        // requests from frontend
        before(UI_ROOT + "*", (req, res) -> {
            if (!"OPTIONS".equals(req.requestMethod())) {
                if (!req.pathInfo().contains(RoutePath.AUTHENTICATION_REQUEST)) {
                    String tokenFromHeader = Utility.getTokenFromHeader(req);

                    boolean isTokenValid = false;
                    if (StringUtils.isNotBlank(tokenFromHeader)) {
                        isTokenValid = new CookieUtil().isCookieValid(req.cookie(cookieName), cookieSalt.getBytes(), tokenFromHeader, jwtSecret);
                        isTokenValid = new JWTRouteFilter(jwtSecret, null).isAccessAllowed(req);

                    }
                    if (!isTokenValid) {
                        halt(401, SecurityUtil.ResultType.AUTHENTICATION_ERROR.toString());
                    }
                }
            }
        });
        setupDDPConfigurationLookup(cfg.getString(ApplicationConfigConstants.DDP));

        AuthenticationRoute authenticationRoute = new AuthenticationRoute(auth0Util,
                jwtSecret, cookieSalt, cookieName, userUtil,
                cfg.getString("portal.environment"));
        post(UI_ROOT + RoutePath.AUTHENTICATION_REQUEST, authenticationRoute, new JsonTransformer());

        KitUtil kitUtil = new KitUtil();

        DDPRequestUtil ddpRequestUtil = new DDPRequestUtil();
        PatchUtil patchUtil = new PatchUtil();

        setupExternalShipperLookup(cfg.getString(ApplicationConfigConstants.EXTERNAL_SHIPPER));
        GBFRequestUtil gbfRequestUtil = new GBFRequestUtil();

        setupShippingRoutes(notificationUtil, auth0Util, userUtil);

        setupMedicalRecordRoutes(cfg, notificationUtil, patchUtil);

        setupMRAbstractionRoutes();

        setupMiscellaneousRoutes();

        setupSharedRoutes(kitUtil, notificationUtil, patchUtil);

        setupPubSubPublisherRoutes(cfg);

        //no GET for USER_SETTINGS_REQUEST because UI gets them per AuthenticationRoute
        patch(UI_ROOT + RoutePath.USER_SETTINGS_REQUEST, new UserSettingRoute(), new JsonTransformer());

        setupJobs(cfg, kitUtil, notificationUtil, eventUtil);

        //TODO - redo with pubsub
        JavaHeapDumper heapDumper = new JavaHeapDumper();
        get(UI_ROOT + "/heapDump", new Route() {
            @Override
            public Object handle(Request request, Response response) throws Exception {
                logger.info("Received request to create java heap dump");
                String gcpName = TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME);
                heapDumper.dumpHeapToBucket(gcpName + "_dsm_heapdumps");
                return null;
            }
        }, new JsonTransformer());
        logger.info("Finished setting up DSM custom routes and jobs...");
    }

    private void setupPubSub(@NonNull Config cfg, NotificationUtil notificationUtil) {
        String projectId = cfg.getString(GCP_PATH_TO_PUBSUB_PROJECT_ID);
        String subscriptionId = cfg.getString(GCP_PATH_TO_PUBSUB_SUB);
        String dsmToDssSubscriptionId = cfg.getString(GCP_PATH_TO_DSS_TO_DSM_SUB);
        String DSMtasksSubscriptionId = cfg.getString(GCP_PATH_TO_DSM_TASKS_SUB);

        logger.info("Setting up pubsub for {}/{}", projectId, subscriptionId);

        try {
            // Instantiate an asynchronous message receiver.
            MessageReceiver receiver =
                    (PubsubMessage message, AckReplyConsumer consumer) -> {
                        // Handle incoming message, then ack the received message.
                        try {
                            TransactionWrapper.inTransaction(conn -> {
                                PubSubLookUp.processCovidTestResults(conn, message, notificationUtil);
                                logger.info("Processing the message finished");
                                consumer.ack();
                                return null;
                            });

                        }catch(Exception ex){
                            logger.info("about to nack the message", ex);
                            consumer.nack();
                            ex.printStackTrace();
                        }
                    };

            Subscriber subscriber = null;
            ProjectSubscriptionName resultSubName = ProjectSubscriptionName.of(projectId, subscriptionId);
            ExecutorProvider resultsSubExecProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();
            subscriber = Subscriber.newBuilder(resultSubName, receiver)
                    .setParallelPullCount(1)
                    .setExecutorProvider(resultsSubExecProvider)
                    .setMaxAckExtensionPeriod(org.threeten.bp.Duration.ofSeconds(120))
                    .build();
            try {
                subscriber.startAsync().awaitRunning(1L, TimeUnit.MINUTES);
                logger.info("Started pubsub subscription receiver for {}", subscriptionId);
            }
            catch (TimeoutException e) {
                throw new RuntimeException("Timed out while starting pubsub subscription " + subscriptionId, e);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to get results from pubsub ", e);
        }

        logger.info("Setting up pubsub for {}/{}", projectId, dsmToDssSubscriptionId);

        try {
            PubSubResultMessageSubscription.dssToDsmSubscriber(projectId, dsmToDssSubscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            DSMtasksSubscription.subscribeDSMtasks(projectId, DSMtasksSubscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("Pubsub setup complete");
    }

    protected void updateDB(@NonNull String dbUrl) {
        logger.info("Running DB update...");

        try (Connection conn = DriverManager.getConnection(dbUrl
                + "&sessionVariables=innodb_strict_mode=on,tx_isolation='READ-COMMITTED',sql_mode='TRADITIONAL'")) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));

            Liquibase liquibase = new liquibase.Liquibase("master-changelog.xml", new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts());
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to run DB update.", e);
        }
    }

    private void setupShippingRoutes(@NonNull NotificationUtil notificationUtil, @NonNull Auth0Util auth0Util, @NonNull UserUtil userUtil) {
        get(UI_ROOT + RoutePath.KIT_REQUESTS_PATH, new KitRequestRoute(), new JsonTransformer());

        KitStatusChangeRoute kitStatusChangeRoute = new KitStatusChangeRoute(notificationUtil);
        post(UI_ROOT + RoutePath.FINAL_SCAN_REQUEST, kitStatusChangeRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.TRACKING_SCAN_REQUEST, kitStatusChangeRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.SENT_KIT_REQUEST, kitStatusChangeRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.RECEIVED_KIT_REQUEST, kitStatusChangeRoute, new JsonTransformer());

        KitDeactivationRoute kitDeactivationRoute = new KitDeactivationRoute(notificationUtil);
        patch(UI_ROOT + RoutePath.DEACTIVATE_KIT_REQUEST, kitDeactivationRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.ACTIVATE_KIT_REQUEST, kitDeactivationRoute, new JsonTransformer());

        patch(UI_ROOT + RoutePath.AUTHORIZE_KIT, new KitAuthorizationRoute(), new JsonTransformer());

        KitExpressRoute kitExpressRoute = new KitExpressRoute(notificationUtil);
        get(UI_ROOT + RoutePath.EXPRESS_KIT_REQUEST, kitExpressRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.EXPRESS_KIT_REQUEST, kitExpressRoute, new JsonTransformer());

        LabelSettingRoute labelSettingRoute = new LabelSettingRoute();
        get(UI_ROOT + RoutePath.LABEL_SETTING_REQUEST, labelSettingRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.LABEL_SETTING_REQUEST, labelSettingRoute, new JsonTransformer());

        post(UI_ROOT + RoutePath.KIT_UPLOAD_REQUEST, new KitUploadRoute(notificationUtil), new JsonTransformer());

        KitLabelRoute kitLabelRoute = new KitLabelRoute();
        get(UI_ROOT + RoutePath.KIT_LABEL_REQUEST, kitLabelRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.KIT_LABEL_REQUEST, kitLabelRoute, new JsonTransformer());

        get(UI_ROOT + RoutePath.SEARCH_KIT, new KitSearchRoute(), new JsonTransformer());

        KitDiscardRoute kitDiscardRoute = new KitDiscardRoute(auth0Util, userUtil);
        get(UI_ROOT + RoutePath.DISCARD_SAMPLES, kitDiscardRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.DISCARD_SAMPLES, kitDiscardRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.DISCARD_UPLOAD, kitDiscardRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.DISCARD_SHOW_UPLOAD, kitDiscardRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.DISCARD_CONFIRM, kitDiscardRoute, new JsonTransformer());
    }

    //Routes used by medical record
    private void setupMedicalRecordRoutes(@NonNull Config cfg, @NonNull NotificationUtil notificationUtil, @NonNull PatchUtil patchUtil) {
        //Medical Record
        get(UI_ROOT + RoutePath.ASSIGNEE_REQUEST, new AssigneeRoute(), new JsonTransformer());

        InstitutionRoute institutionRoute = new InstitutionRoute();
        post(UI_ROOT + RoutePath.INSTITUTION_REQUEST, institutionRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.INSTITUTION_REQUEST, institutionRoute, new JsonTransformer());

        DownloadPDFRoute pdfRoute = new DownloadPDFRoute();
        post(UI_ROOT + RoutePath.DOWNLOAD_PDF + DownloadPDFRoute.PDF, pdfRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.DOWNLOAD_PDF + DownloadPDFRoute.BUNDLE, pdfRoute, new JsonTransformer());
        get(UI_ROOT + DownloadPDFRoute.PDF, pdfRoute, new JsonTransformer());

        patch(UI_ROOT + RoutePath.ASSIGN_PARTICIPANT_REQUEST, new AssignParticipantRoute(
                cfg.getString(ApplicationConfigConstants.GET_DDP_PARTICIPANT_ID),
                cfg.getString(ApplicationConfigConstants.EMAIL_FRONTEND_URL_FOR_LINKS), notificationUtil), new JsonTransformer());

        ViewFilterRoute viewFilterRoute = new ViewFilterRoute(patchUtil);
        //gets filter names for user for this realm (shared filters and user's filters
        get(UI_ROOT + RoutePath.GET_FILTERS, viewFilterRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.GET_DEFAULT_FILTERS, viewFilterRoute, new JsonTransformer());
        //saves the current Filter Parameters with a name for future use
        patch(UI_ROOT + RoutePath.SAVE_FILTER, viewFilterRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.FILTER_DEFAULT, viewFilterRoute, new JsonTransformer());

        FilterRoute filterRoute = new FilterRoute();
        //returns List[] that is filtered based on the filterName
        get(UI_ROOT + RoutePath.APPLY_FILTER, filterRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.FILTER_LIST, filterRoute, new JsonTransformer());
        //gets the participant to go to the tissue that was clicked on
        get(UI_ROOT + RoutePath.GET_PARTICIPANT, new GetParticipantRoute(), new JsonTransformer());

        MedicalRecordLogRoute medicalRecordLogRoute = new MedicalRecordLogRoute();
        get(UI_ROOT + RoutePath.MEDICAL_RECORD_LOG_REQUEST, medicalRecordLogRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.MEDICAL_RECORD_LOG_REQUEST, medicalRecordLogRoute, new JsonTransformer());

        PermalinkRoute permalinkRoute = new PermalinkRoute();
        get(UI_ROOT + RoutePath.PERMALINK_PARTICIPANT_REQUEST, permalinkRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.PERMALINK_INSTITUTION_REQUEST, permalinkRoute, new JsonTransformer());

        get(UI_ROOT + RoutePath.LOOKUP, new LookupRoute(), new JsonTransformer());

        FieldSettingsRoute fieldSettingsRoute = new FieldSettingsRoute();
        get(UI_ROOT + RoutePath.FIELD_SETTINGS_ROUTE, fieldSettingsRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.FIELD_SETTINGS_ROUTE, fieldSettingsRoute, new JsonTransformer());

        get(UI_ROOT + RoutePath.DISPLAY_SETTINGS_ROUTE, new DisplaySettingsRoute(patchUtil), new JsonTransformer());
    }

    private void setupMRAbstractionRoutes() {
        AbstractionFormControlRoute abstractionFormControlRoute = new AbstractionFormControlRoute();
        get(UI_ROOT + RoutePath.ABSTRACTION_FORM_CONTROLS, abstractionFormControlRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.ABSTRACTION_FORM_CONTROLS, abstractionFormControlRoute, new JsonTransformer());

        post(UI_ROOT + RoutePath.ABSTRACTION, new AbstractionRoute(), new JsonTransformer());
    }

    private void setupMiscellaneousRoutes() {
        MailingListRoute mailingListRoute = new MailingListRoute();
        get(UI_ROOT + RoutePath.MAILING_LIST_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, mailingListRoute, new JsonTransformer());

        ParticipantExitRoute participantExitRoute = new ParticipantExitRoute();
        get(UI_ROOT + RoutePath.PARTICIPANT_EXIT_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, participantExitRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.PARTICIPANT_EXIT_REQUEST, participantExitRoute, new JsonTransformer());

        TriggerSurveyRoute triggerSurveyRoute = new TriggerSurveyRoute();
        get(UI_ROOT + RoutePath.TRIGGER_SURVEY + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, triggerSurveyRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.TRIGGER_SURVEY, triggerSurveyRoute, new JsonTransformer());

        get(UI_ROOT + RoutePath.EVENT_TYPES + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, new EventTypeRoute(), new JsonTransformer());

        ParticipantEventRoute participantEventRoute = new ParticipantEventRoute();
        get(UI_ROOT + RoutePath.PARTICIPANT_EVENTS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, participantEventRoute, new JsonTransformer());
        post(UI_ROOT + RoutePath.SKIP_PARTICIPANT_EVENTS, participantEventRoute, new JsonTransformer());

        post(UI_ROOT + RoutePath.NDI_REQUEST, new NDIRoute(), new JsonTransformer());

        DrugListRoute drugListRoute = new DrugListRoute();
        get(UI_ROOT + RoutePath.FULL_DRUG_LIST_REQUEST, drugListRoute, new JsonTransformer());
        patch(UI_ROOT + RoutePath.FULL_DRUG_LIST_REQUEST, drugListRoute, new JsonTransformer());

        AddFamilyMemberRoute addFamilyMemberRoute = new AddFamilyMemberRoute();
        post(UI_ROOT + RoutePath.ADD_FAMILY_MEMBER, addFamilyMemberRoute, new JsonTransformer());

        GetParticipantDataRoute getParticipantDataRoute = new GetParticipantDataRoute();
        get(UI_ROOT + RoutePath.GET_PARTICIPANT_DATA, getParticipantDataRoute, new JsonTransformer());
    }

    private void setupSharedRoutes(@NonNull KitUtil kitUtil, @NonNull NotificationUtil notificationUtil,
                                   @NonNull PatchUtil patchUtil) {
        DashboardRoute dashboardRoute = new DashboardRoute(kitUtil);
        get(UI_ROOT + RoutePath.DASHBOARD_REQUEST, dashboardRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.DASHBOARD_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.START + RoutePath.ROUTE_SEPARATOR + RequestParameter.END, dashboardRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.SAMPLE_REPORT_REQUEST, dashboardRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.SAMPLE_REPORT_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.START + RoutePath.ROUTE_SEPARATOR + RequestParameter.END, dashboardRoute, new JsonTransformer());

        AllowedRealmsRoute allowedRealmsRoute = new AllowedRealmsRoute();
        get(UI_ROOT + RoutePath.ALLOWED_REALMS_REQUEST, allowedRealmsRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.STUDIES, allowedRealmsRoute, new JsonTransformer());

        KitTypeRoute kitTypeRoute = new KitTypeRoute(kitUtil);
        get(UI_ROOT + RoutePath.KIT_TYPES_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, kitTypeRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.UPLOAD_REASONS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, kitTypeRoute, new JsonTransformer());
        get(UI_ROOT + RoutePath.CARRIERS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM,  new CarrierServiceRoute(), new JsonTransformer());

        patch(UI_ROOT + RoutePath.PATCH, new PatchRoute(notificationUtil, patchUtil), new JsonTransformer());
    }

    private void setupPubSubPublisherRoutes(Config config) {
        String projectId = config.getString(GCP_PATH_TO_PUBSUB_PROJECT_ID);
        String dsmToDssTopicId = config.getString(GCP_PATH_TO_DSM_TO_DSS_TOPIC);

        EditParticipantPublisherRoute editParticipantPublisherRoute = new EditParticipantPublisherRoute(projectId, dsmToDssTopicId);
        put(UI_ROOT + RoutePath.EDIT_PARTICIPANT, editParticipantPublisherRoute, new JsonTransformer());

        EditParticipantMessageReceiverRoute editParticipantMessageReceiverRoute = new EditParticipantMessageReceiverRoute();
        get(UI_ROOT + RoutePath.EDIT_PARTICIPANT_MESSAGE, editParticipantMessageReceiverRoute, new JsonTransformer());
    }

    private void setupJobs(@NonNull Config cfg, @NonNull KitUtil kitUtil,
                           @NonNull NotificationUtil notificationUtil, @NonNull EventUtil eventUtil) {
        String schedulerName = null;
        if (cfg.getBoolean(ApplicationConfigConstants.QUARTZ_ENABLE_JOBS)) {
            logger.info("Setting up jobs");
            try {
                Scheduler scheduler = new StdSchedulerFactory().getScheduler();
                schedulerName = scheduler.getSchedulerName();
                createDDPRequestScheduledJobs(scheduler, DDPRequestJob.class, "DDPREQUEST_JOB",
                        cfg.getInt(ApplicationConfigConstants.QUARTZ_DDP_REQUEST_JOB_INTERVAL_SEC),
                        new DDPRequestTriggerListener(), notificationUtil);

                createScheduledJob(scheduler, cfg,
                        NotificationJob.class, "NOTIFICATION_JOB",
                        cfg.getInt(ApplicationConfigConstants.QUARTZ_NOTIFICATION_JOB_INTERVAL_SEC),
                        new NotificationTriggerListener());

                createScheduledJob(scheduler, cfg,
                        LabelCreationJob.class, "LABEL_CREATION_JOB",
                        cfg.getInt(ApplicationConfigConstants.QUARTZ_LABEL_CREATION_JOB_INTERVAL_SEC),
                        new LabelCreationTriggerListener());

                createScheduleJob(scheduler, cfg, notificationUtil, kitUtil,
                        GPNotificationJob.class, "GP_SCHEDULE_JOB",
                        cfg.getString(ApplicationConfigConstants.EMAIL_CRON_EXPRESSION_FOR_GP_NOTIFICATION));

                createScheduleJob(scheduler, eventUtil, notificationUtil, DDPEventJob.class, "TRIGGER_DDP_EVENT",
                        cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_EXPRESSION_FOR_DDP_EVENT_TRIGGER), new DDPEventTriggerListener(), null);

                // currently not needed anymore but might come back
                createScheduleJob(scheduler, eventUtil, notificationUtil,
                        ExternalShipperJob.class, "CHECK_EXTERNAL_SHIPPER",
                        cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_EXPRESSION_FOR_EXTERNAL_SHIPPER),
                        new ExternalShipperTriggerListener(), cfg);

                createScheduleJob(scheduler, null, null, EasypostShipmentStatusJob.class, "CHECK_STATUS_SHIPMENT",
                        cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_STATUS_SHIPMENT), new EasypostShipmentStatusTriggerListener(), cfg);


                logger.info("Setup Job Scheduler...");
                try {
                    scheduler.start();
                    logger.info("Job Scheduler setup complete.");
                }
                catch (Exception ex) {
                    throw new RuntimeException("Unable to setup Job Scheduler.", ex);
                }
            }
            catch (SchedulerException e) {
                throw new RuntimeException("Could not create scheduler ", e);
            }
        }
        setupErrorNotifications(cfg, schedulerName);
    }


    @Override
    protected void setupErrorNotifications(Config config, String schedulerName) {
        if (config == null) {
            throw new IllegalArgumentException("Config should be provided");
        } else {
            logger.info("Setup error notifications...");
            if (config.hasPath("slack.hook") && config.hasPath("slack.channel")) {
                String appEnv = config.getString("portal.environment");
                String slackHookUrlString = config.getString("slack.hook");
                String gcpServiceName = config.getString("slack.gcpServiceName");
                String rootPackage = DSMServer.class.getPackageName();
                URI slackHookUrl;
                String slackChannel = config.getString("slack.channel");
                try {
                    slackHookUrl = new URI(slackHookUrlString);
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Could not parse " + slackHookUrlString + "\n" + e);
                }
                SlackAppender.configure(schedulerName, appEnv, slackHookUrl, slackChannel, gcpServiceName, rootPackage);
                logger.info("Error notification setup complete. If log4j.xml is configured, notifications will be sent to " + slackChannel + ".");
            } else {
                logger.warn("Skipping error notification setup.");
            }

        }
    }

    /**
     * Job to request ddp kitRequests.
     */
    public static void createDDPRequestScheduledJobs(@NonNull Scheduler scheduler, @NonNull Class<? extends Job> jobClass,
                                                     @NonNull String identity, @NonNull int jobIntervalInSeconds,
                                                     BasicTriggerListener triggerListener, @NonNull NotificationUtil notificationUtil) throws SchedulerException {
        //create job
        JobDetail job = JobBuilder.newJob(jobClass)
                .withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP + ".DSM").build();

        //pass parameters to JobDataMap for JobDetail
        job.getJobDataMap().put(NOTIFICATION_UTIL, notificationUtil);

        //create trigger
        TriggerKey triggerKey = new TriggerKey(identity + "_TRIGGER", "DDP");
        SimpleTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey).withSchedule(simpleSchedule()
                        .withIntervalInSeconds(jobIntervalInSeconds).repeatForever()).build();

        //add job
        scheduler.scheduleJob(job, trigger);
        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(triggerListener, KeyMatcher.keyEquals(triggerKey));
    }

    /**
     * Job to sent notification email
     */
    public static void createScheduledJob(@NonNull Scheduler scheduler, @NonNull Config config, @NonNull Class<? extends Job> jobClass,
                                          @NonNull String identity, @NonNull int jobIntervalInSeconds,
                                          @NonNull BasicTriggerListener triggerListener) throws SchedulerException {
        //create job
        JobDetail job = JobBuilder.newJob(jobClass)
                .withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP).build();

        //pass parameters to JobDataMap for JobDetail
        job.getJobDataMap().put(CONFIG, config);

        //create trigger
        TriggerKey triggerKey = new TriggerKey(identity + "_TRIGGER", "DDP");
        SimpleTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey).withSchedule(simpleSchedule()
                        .withIntervalInSeconds(jobIntervalInSeconds).repeatForever()).build();

        //add job
        scheduler.scheduleJob(job, trigger);
        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(triggerListener, KeyMatcher.keyEquals(triggerKey));
    }

    /**
     * Job to sent GP notification email
     */
    public static void createScheduleJob(@NonNull Scheduler scheduler, @NonNull Config config, @NonNull NotificationUtil notificationUtil,
                                         @NonNull KitUtil kitUtil, @NonNull Class<? extends Job> jobClass,
                                         @NonNull String identity, @NonNull String cronExpression) throws SchedulerException {
        //create job
        JobDetail job = JobBuilder.newJob(jobClass)
                .withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP).build();

        //pass parameters to JobDataMap for JobDetail
        job.getJobDataMap().put(CONFIG, config);
        job.getJobDataMap().put(NOTIFICATION_UTIL, notificationUtil);
        job.getJobDataMap().put(KIT_UTIL, kitUtil);

        //create trigger
        TriggerKey triggerKey = new TriggerKey(identity + "_TRIGGER", "DDP");
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey).withSchedule(cronSchedule(cronExpression)).build();
        //add job
        scheduler.scheduleJob(job, trigger);
        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(new GPNotificationTriggerListener(), KeyMatcher.keyEquals(triggerKey));
    }

    /**
     * Job to trigger ddp reminder emails and external shipper
     */
    public static void createScheduleJob(@NonNull Scheduler scheduler, EventUtil eventUtil, NotificationUtil notificationUtil,
                                         @NonNull Class<? extends Job> jobClass,
                                         @NonNull String identity, @NonNull String cronExpression, @NonNull BasicTriggerListener basicTriggerListener,
                                         Config config) throws SchedulerException {
        //create job
        JobDetail job = JobBuilder.newJob(jobClass)
                .withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP).build();

        if (eventUtil != null) {
            //pass parameters to JobDataMap for JobDetail
            job.getJobDataMap().put(EVENT_UTIL, eventUtil);
        }
        if (notificationUtil != null) {
            //pass parameters to JobDataMap for JobDetail
            job.getJobDataMap().put(NOTIFICATION_UTIL, notificationUtil);
        }
        //         currently not needed anymore but might come back
        if (jobClass == ExternalShipperJob.class) {
            job.getJobDataMap().put(ADDITIONAL_CRON_EXPRESSION, config.getString(ApplicationConfigConstants.QUARTZ_CRON_EXPRESSION_FOR_EXTERNAL_SHIPPER_ADDITIONAL));
        }

        logger.info(cronExpression);

        //create trigger
        TriggerKey triggerKey = new TriggerKey(identity + "_TRIGGER", "DDP");
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey).withSchedule(cronSchedule(cronExpression)).build();
        //add job
        scheduler.scheduleJob(job, trigger);

        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(basicTriggerListener, KeyMatcher.keyEquals(triggerKey));
    }

    public static void setupDDPConfigurationLookup(@NonNull String ddpConf) {
        JsonArray array = (JsonArray) (new JsonParser().parse(ddpConf));
        for (JsonElement ddpInfo : array) {
            if (ddpInfo.isJsonObject()) {
                ddpConfigurationLookup.put(ddpInfo.getAsJsonObject().get(ApplicationConfigConstants.INSTANCE_NAME).getAsString().toLowerCase(), ddpInfo);
            }
        }
    }

    public static String getDDPTokenSecret(@NonNull String instanceName) {
        JsonElement jsonElement = ddpConfigurationLookup.get(instanceName.toLowerCase());
        if (jsonElement != null && jsonElement.getAsJsonObject().has(ApplicationConfigConstants.TOKEN_SECRET)) {
            return jsonElement.getAsJsonObject().get(ApplicationConfigConstants.TOKEN_SECRET).getAsString();
        }
        return null;
    }

    public static String getDDPEasypostApiKey(@NonNull String instanceName) {
        JsonElement jsonElement = ddpConfigurationLookup.get(instanceName.toLowerCase());
        if (jsonElement != null) {
            return jsonElement.getAsJsonObject().get(ApplicationConfigConstants.EASYPOST_API_KEY).getAsString();
        }
        return null;
    }

    public static Auth0Util getAuth0Util() {
        return auth0Util;
    }

    // currently not needed anymore but might come back
    public static void setupExternalShipperLookup(@NonNull String externalSipperConf) {
        JsonArray array = (JsonArray) (new JsonParser().parse(externalSipperConf));
        for (JsonElement ddpInfo : array) {
            if (ddpInfo.isJsonObject()) {
                ddpConfigurationLookup.put(ddpInfo.getAsJsonObject().get(ApplicationConfigConstants.SHIPPER_NAME).getAsString().toLowerCase(), ddpInfo);
            }
        }
    }

    public static String getApiKey(@NonNull String shipperName) {
        JsonElement jsonElement = ddpConfigurationLookup.get(shipperName.toLowerCase());
        if (jsonElement != null) {
            return jsonElement.getAsJsonObject().get(ApplicationConfigConstants.API_KEY).getAsString();
        }
        return null;
    }

    public static String getBaseUrl(@NonNull String shipperName) {
        JsonElement jsonElement = ddpConfigurationLookup.get(shipperName.toLowerCase());
        if (jsonElement != null) {
            return jsonElement.getAsJsonObject().get(ApplicationConfigConstants.BASE_URL).getAsString();
        }
        return null;
    }

    public static String getClassName(@NonNull String shipperName) {
        JsonElement jsonElement = ddpConfigurationLookup.get(shipperName.toLowerCase());
        if (jsonElement != null) {
            return jsonElement.getAsJsonObject().get(ApplicationConfigConstants.CLASS_NAME).getAsString();
        }
        return null;
    }

    public static boolean isTest(@NonNull String shipperName) {
        JsonElement jsonElement = ddpConfigurationLookup.get(shipperName.toLowerCase());
        if (jsonElement != null) {
            return jsonElement.getAsJsonObject().get(ApplicationConfigConstants.TEST).getAsBoolean();
        }
        return true;
    }

    private static void registerAppEngineStartupCallback(long bootTimeoutSeconds) {
        // Block until isReady is available, with an optional timeout to prevent
        // instance for sitting around too long in a nonresponsive state.  There is a
        // judgement call to be made here to allow for lengthy liquibase migrations during boot.
        logger.info("Will wait for at most {} seconds for boot before GAE termination", bootTimeoutSeconds);
        get("/_ah/start", new ReadinessRoute(bootTimeoutSeconds));
    }

    private static class ReadinessRoute implements Route {

        private final long bootTimeoutSeconds;

        public ReadinessRoute(long bootTimeoutSeconds) {
            this.bootTimeoutSeconds = bootTimeoutSeconds;
        }

        @Override
        public Object handle(Request req, Response res) throws Exception {
            AtomicInteger status = new AtomicInteger(HttpStatus.SC_SERVICE_UNAVAILABLE);
            long bootTime = Instant.now().toEpochMilli();
            Thread waitForBoot = new Thread(() -> {
                synchronized (isReady) {
                    if (isReady.get()) {
                        status.set(HttpStatus.SC_OK);
                    }
                }
            });

            waitForBoot.start();
            waitForBoot.join(bootTimeoutSeconds * 1000);
            logger.info("Responding to startup route after {}ms delay with {}", Instant.now().toEpochMilli() - bootTime, status.get());
            res.status(status.get());
            return "";
        }
    }

    protected static void enableCORS(String allowedOrigins, String methods, String headers) {
        Spark.options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }
            if (accessControlRequestMethod != null || accessControlRequestHeaders != null) {
                response.header("Access-Control-Max-Age", "172800");
            }

            return "OK";
        });
        Spark.before((request, response) -> {
            String origin = request.headers("Origin");
            response.header("Access-Control-Allow-Origin", (StringUtils.isNotBlank(origin) && allowedOrigins.contains(origin)) ? origin : "");
            response.header("Access-Control-Request-Method", methods);
            response.header("Access-Control-Allow-Headers", headers);
            response.header("Access-Control-Allow-Credentials", "true");
            response.type("application/json");
        });
    }

}
