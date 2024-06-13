package org.broadinstitute.dsm;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static spark.Spark.afterAfter;
import static spark.Spark.before;
import static spark.Spark.delete;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.patch;
import static spark.Spark.post;
import static spark.Spark.put;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.net.MediaType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.broadinstitute.ddp.appengine.spark.SparkBootUtil;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPInternalError;
import org.broadinstitute.ddp.logging.LogUtil;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.kit.KitDao;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.DuplicateEntityException;
import org.broadinstitute.dsm.exception.EntityNotFound;
import org.broadinstitute.dsm.exception.UnsafeDeleteError;
import org.broadinstitute.dsm.jobs.DDPEventJob;
import org.broadinstitute.dsm.jobs.DDPRequestJob;
import org.broadinstitute.dsm.jobs.EasypostShipmentStatusJob;
import org.broadinstitute.dsm.jobs.GPNotificationJob;
import org.broadinstitute.dsm.jobs.LabelCreationJob;
import org.broadinstitute.dsm.jobs.NotificationJob;
import org.broadinstitute.dsm.pubsub.AntivirusScanningStatusListener;
import org.broadinstitute.dsm.pubsub.DSMtasksSubscription;
import org.broadinstitute.dsm.pubsub.MercuryOrderStatusListener;
import org.broadinstitute.dsm.pubsub.PubSubResultMessageSubscription;
import org.broadinstitute.dsm.route.AbstractionFormControlRoute;
import org.broadinstitute.dsm.route.AbstractionRoute;
import org.broadinstitute.dsm.route.AllowedRealmsRoute;
import org.broadinstitute.dsm.route.AssignParticipantRoute;
import org.broadinstitute.dsm.route.AssigneeRoute;
import org.broadinstitute.dsm.route.AuthenticationRoute;
import org.broadinstitute.dsm.route.BSPKitRegisteredRoute;
import org.broadinstitute.dsm.route.BSPKitRoute;
import org.broadinstitute.dsm.route.CancerRoute;
import org.broadinstitute.dsm.route.CarrierServiceRoute;
import org.broadinstitute.dsm.route.ClinicalKitsRoute;
import org.broadinstitute.dsm.route.CreateBSPDummyKitRoute;
import org.broadinstitute.dsm.route.CreateClinicalDummyKitRoute;
import org.broadinstitute.dsm.route.DashboardRoute;
import org.broadinstitute.dsm.route.DisplaySettingsRoute;
import org.broadinstitute.dsm.route.DownloadPDFRoute;
import org.broadinstitute.dsm.route.DownloadParticipantListRoute;
import org.broadinstitute.dsm.route.DrugListRoute;
import org.broadinstitute.dsm.route.DrugRoute;
import org.broadinstitute.dsm.route.EditParticipantMessageReceiverRoute;
import org.broadinstitute.dsm.route.EditParticipantPublisherRoute;
import org.broadinstitute.dsm.route.EventTypeRoute;
import org.broadinstitute.dsm.route.FieldSettingsRoute;
import org.broadinstitute.dsm.route.FilterRoute;
import org.broadinstitute.dsm.route.InstitutionRoute;
import org.broadinstitute.dsm.route.KitAuthorizationRoute;
import org.broadinstitute.dsm.route.KitDeactivationRoute;
import org.broadinstitute.dsm.route.KitDiscardRoute;
import org.broadinstitute.dsm.route.KitExpressRoute;
import org.broadinstitute.dsm.route.KitLabelRoute;
import org.broadinstitute.dsm.route.KitRequestRoute;
import org.broadinstitute.dsm.route.KitSearchRoute;
import org.broadinstitute.dsm.route.KitTypeRoute;
import org.broadinstitute.dsm.route.KitUploadRoute;
import org.broadinstitute.dsm.route.LabelSettingRoute;
import org.broadinstitute.dsm.route.LoggingFilter;
import org.broadinstitute.dsm.route.LookupRoute;
import org.broadinstitute.dsm.route.MailingListRoute;
import org.broadinstitute.dsm.route.MedicalRecordLogRoute;
import org.broadinstitute.dsm.route.NDIRoute;
import org.broadinstitute.dsm.route.OncHistoryTemplateRoute;
import org.broadinstitute.dsm.route.OncHistoryUploadRoute;
import org.broadinstitute.dsm.route.ParticipantExitRoute;
import org.broadinstitute.dsm.route.ParticipantStatusRoute;
import org.broadinstitute.dsm.route.PatchRoute;
import org.broadinstitute.dsm.route.SkippedParticipantEventRoute;
import org.broadinstitute.dsm.route.TriggerSomaticResultSurveyRoute;
import org.broadinstitute.dsm.route.TriggerSurveyRoute;
import org.broadinstitute.dsm.route.UserSettingRoute;
import org.broadinstitute.dsm.route.ViewFilterRoute;
import org.broadinstitute.dsm.route.admin.AdminOperationRoute;
import org.broadinstitute.dsm.route.admin.StudyRoleRoute;
import org.broadinstitute.dsm.route.admin.UserRoleRoute;
import org.broadinstitute.dsm.route.admin.UserRoute;
import org.broadinstitute.dsm.route.dashboard.NewDashboardRoute;
import org.broadinstitute.dsm.route.familymember.AddFamilyMemberRoute;
import org.broadinstitute.dsm.route.juniper.JuniperShipKitRoute;
import org.broadinstitute.dsm.route.juniper.StatusKitRoute;
import org.broadinstitute.dsm.route.kit.KitFinalScanRoute;
import org.broadinstitute.dsm.route.kit.KitInitialScanRoute;
import org.broadinstitute.dsm.route.kit.KitTrackingScanRoute;
import org.broadinstitute.dsm.route.kit.RGPKitFinalScanRoute;
import org.broadinstitute.dsm.route.kit.ReceivedKitsRoute;
import org.broadinstitute.dsm.route.kit.SentKitRoute;
import org.broadinstitute.dsm.route.mercury.GetMercuryEligibleSamplesRoute;
import org.broadinstitute.dsm.route.mercury.GetMercuryOrdersRoute;
import org.broadinstitute.dsm.route.mercury.PostMercuryOrderDummyRoute;
import org.broadinstitute.dsm.route.mercury.PostMercuryOrderRoute;
import org.broadinstitute.dsm.route.participant.GetParticipantDataRoute;
import org.broadinstitute.dsm.route.participant.GetParticipantRoute;
import org.broadinstitute.dsm.route.participantfiles.DownloadParticipantFileRoute;
import org.broadinstitute.dsm.route.phimanifest.PhiManifestReportRoute;
import org.broadinstitute.dsm.route.somaticresults.DeleteSomaticResultRoute;
import org.broadinstitute.dsm.route.somaticresults.GetSomaticResultsRoute;
import org.broadinstitute.dsm.route.somaticresults.PostSomaticResultUploadRoute;
import org.broadinstitute.dsm.route.tag.cohort.BulkCreateCohortTagRoute;
import org.broadinstitute.dsm.route.tag.cohort.CreateCohortTagRoute;
import org.broadinstitute.dsm.route.tag.cohort.DeleteCohortTagRoute;
import org.broadinstitute.dsm.route.util.JacksonResponseTransformer;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.dsm.service.EventService;
import org.broadinstitute.dsm.service.FileDownloadService;
import org.broadinstitute.dsm.service.SomaticResultUploadService;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.JWTRouteFilter;
import org.broadinstitute.dsm.util.JavaHeapDumper;
import org.broadinstitute.dsm.util.JsonNullTransformer;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.SecurityUtil;
import org.broadinstitute.dsm.util.triggerlistener.DDPEventTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.DDPRequestTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.EasypostShipmentStatusTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.GPNotificationTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.LabelCreationTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.NotificationTriggerListener;
import org.broadinstitute.lddp.exception.InvalidTokenException;
import org.broadinstitute.lddp.util.BasicTriggerListener;
import org.broadinstitute.lddp.util.GsonResponseTransformer;
import org.broadinstitute.lddp.util.Utility;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.KeyMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

public class DSMServer {

    public static final String CONFIG = "config";
    public static final String NOTIFICATION_UTIL = "NotificationUtil";
    public static final String KIT_UTIL = "KitUtil";
    public static final String DDP_UTIL = "DDPRequestUtil";
    public static final String EVENT_UTIL = "EventUtil";
    public static final String ADDITIONAL_CRON_EXPRESSION = "externalShipper_cron_expression_additional";
    public static final String GCP_PATH_TO_SERVICE_ACCOUNT = "portal.googleProjectCredentials";
    public static final String IS_PRODUCTION = "ui.production";
    public static final String UNDERSCORE_TRIGGER = "_TRIGGER";
    public static final String UPS_PATH_TO_USERNAME = "ups.username";
    public static final String UPS_PATH_TO_PASSWORD = "ups.password";
    public static final String UPS_PATH_TO_ACCESSKEY = "ups.accesskey";
    public static final String UPS_PATH_TO_ENDPOINT = "ups.url";
    public static final String GCP_PATH_DSM_MERCURY_TOPIC = "pubsub.dsm_to_mercury_topic";
    public static final String GCP_PATH_MERCURY_DSM_SUB = "pubsub.mercury_to_dsm_subscription";
    public static final String GCP_PATH_ANTI_VIRUS_SUB = "pubsub.antivirus_to_dsm_subscription";
    private static final Logger logger = LoggerFactory.getLogger(DSMServer.class);
    private static final String GCP_PATH_PUBSUB_PROJECT_ID = "pubsub.projectId";
    private static final String GCP_PATH_DSM_DSS_SUB = "pubsub.dss_to_dsm_subscription";
    private static final String GCP_PATH_DSM_DSS_TOPIC = "pubsub.dsm_to_dss_topic";
    private static final String GCP_PATH_DSM_TASKS_SUB = "pubsub.dsm_tasks_subscription";
    private static final String API_ROOT = "/ddp/";
    private static final String DSM_ROOT = "/dsm/";
    private static final String UI_ROOT = "/ui/";
    private static final String INFO_ROOT = "/info/";
    private static final String KDUX_SIGNER = "org.broadinstitute.kdux";
    private static final String[] CORS_HTTP_METHODS = new String[] {"GET", "PUT", "POST", "OPTIONS", "PATCH"};
    private static final String[] CORS_HTTP_HEADERS =
            new String[] {"Content-Type", "Authorization", "X-Requested-With", "Content-Length", "Accept", "Origin", ""};
    private static final String VAULT_CONF = "vault.conf";
    private static final String GAE_DEPLOY_DIR = "appengine/deploy";
    private static final AtomicBoolean isReady = new AtomicBoolean(false);
    private static Map<String, JsonElement> ddpConfigurationLookup = new HashMap<>();
    private static Auth0Util auth0Util;

    private static Subscriber dssSubsciber;

    private static Subscriber mercuryOrderSubscriber;

    private static Subscriber dsmTasksSubscriber;

    private static Subscriber antivirusSubscriber;

    private static Scheduler scheduler;

    public static void main(String[] args) {
        //config without secrets
        Config cfg = ConfigFactory.load();
        //secrets from vault in a config file
        File vaultConfigInCwd = new File(VAULT_CONF);
        File vaultConfigInDeployDir = new File(GAE_DEPLOY_DIR, VAULT_CONF);
        File vaultConfig = vaultConfigInCwd.exists() ? vaultConfigInCwd : vaultConfigInDeployDir;
        logger.info("Reading config values from {}", vaultConfig.getAbsolutePath());

        if (cfg.hasPath(ApplicationConfigConstants.EMAIL_NOTIFICATIONS)) {
            logger.warn("{} should be in environment-specific configuration, not static source code",
                    ApplicationConfigConstants.EMAIL_NOTIFICATIONS);
        }
        cfg = cfg.withFallback(ConfigFactory.parseFile(vaultConfig));

        if (cfg.hasPath(GCP_PATH_TO_SERVICE_ACCOUNT) && StringUtils.isNotBlank(cfg.getString(GCP_PATH_TO_SERVICE_ACCOUNT))) {
            System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", cfg.getString(GCP_PATH_TO_SERVICE_ACCOUNT));
        }
        LogUtil.addAppEngineEnvVarsToMDC();
        // respond GAE dispatcher endpoints as soon as possible
        // immediately lock isReady so that ah/start route will wait

        SparkBootUtil.startSparkServer(new SparkBootUtil.AppEngineShutdown() {
            public void onAhStop() {
                logger.info("Shutting down DSM instance {}", LogUtil.getAppEngineInstance());
                shutdown();
            }

            public void onTerminate() {
                logger.info("Terminating DSM instance {}", LogUtil.getAppEngineInstance());
                shutdown();
            }
        },
                cfg.getConfig("portal"));
        synchronized (isReady) {
            try {
                logger.info("Starting up DSM");
                DSMConfig.setConfig(cfg);
                DSMServer server = new DSMServer();
                server.configureServer(cfg);
                isReady.set(true);
                logger.info("DSM SparkBootUtil Complete");
            } catch (Exception e) {
                logger.error("Error starting DSM server {}", e.toString());
                e.printStackTrace();
            }
        }
    }

    /**
     * Job to request ddp kitRequests.
     */
    public static void createDDPRequestScheduledJobs(@NonNull Scheduler scheduler, @NonNull Class<? extends Job> jobClass,
                                                     @NonNull String identity, @NonNull int jobIntervalInSeconds,
                                                     BasicTriggerListener triggerListener, @NonNull NotificationUtil notificationUtil)
            throws SchedulerException {
        //create job
        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP + ".DSM").build();

        //pass parameters to JobDataMap for JobDetail
        job.getJobDataMap().put(NOTIFICATION_UTIL, notificationUtil);

        //create trigger
        TriggerKey triggerKey = new TriggerKey(identity + UNDERSCORE_TRIGGER, "DDP");
        SimpleTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey)
                .withSchedule(simpleSchedule().withIntervalInSeconds(jobIntervalInSeconds).repeatForever()).build();

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
        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP).build();

        //pass parameters to JobDataMap for JobDetail
        job.getJobDataMap().put(CONFIG, config);

        //create trigger
        TriggerKey triggerKey = new TriggerKey(identity + UNDERSCORE_TRIGGER, "DDP");
        SimpleTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey)
                .withSchedule(simpleSchedule().withIntervalInSeconds(jobIntervalInSeconds).repeatForever()).build();

        //add job
        scheduler.scheduleJob(job, trigger);
        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(triggerListener, KeyMatcher.keyEquals(triggerKey));
    }

    /**
     * Job to sent GP notification email
     */
    public static void createScheduleJob(@NonNull Scheduler scheduler, @NonNull Config config, @NonNull NotificationUtil notificationUtil,
                                         @NonNull KitUtil kitUtil, @NonNull Class<? extends Job> jobClass, @NonNull String identity,
                                         @NonNull String cronExpression) throws SchedulerException {
        //create job
        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP).build();

        //pass parameters to JobDataMap for JobDetail
        job.getJobDataMap().put(CONFIG, config);
        job.getJobDataMap().put(NOTIFICATION_UTIL, notificationUtil);
        job.getJobDataMap().put(KIT_UTIL, kitUtil);

        //create trigger
        TriggerKey triggerKey = new TriggerKey(identity + UNDERSCORE_TRIGGER, "DDP");
        CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).withSchedule(cronSchedule(cronExpression)).build();
        //add job
        scheduler.scheduleJob(job, trigger);
        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(new GPNotificationTriggerListener(), KeyMatcher.keyEquals(triggerKey));
    }

    /**
     * Job to trigger ddp reminder emails and external shipper
     */
    public static void createScheduleJob(@NonNull Scheduler scheduler, EventService eventService, NotificationUtil notificationUtil,
                                         @NonNull Class<? extends Job> jobClass, @NonNull String identity, @NonNull String cronExpression,
                                         @NonNull BasicTriggerListener basicTriggerListener) throws SchedulerException {
        //create job
        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP).build();

        if (eventService != null) {
            //pass parameters to JobDataMap for JobDetail
            job.getJobDataMap().put(EVENT_UTIL, eventService);
        } else {
            logger.error("EventService is null, some jobs might not work properly");
        }
        if (notificationUtil != null) {
            //pass parameters to JobDataMap for JobDetail
            job.getJobDataMap().put(NOTIFICATION_UTIL, notificationUtil);
        }
        //         currently not needed anymore but might come back
        //        if (jobClass == ExternalShipperJob.class) {
        //            job.getJobDataMap().put(ADDITIONAL_CRON_EXPRESSION,
        //                    config.getString(ApplicationConfigConstants.QUARTZ_CRON_EXPRESSION_FOR_EXTERNAL_SHIPPER_ADDITIONAL));
        //        }

        logger.info(cronExpression);

        //create trigger
        TriggerKey triggerKey = new TriggerKey(identity + UNDERSCORE_TRIGGER, "DDP");
        CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).withSchedule(cronSchedule(cronExpression)).build();
        //add job
        scheduler.scheduleJob(job, trigger);

        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(basicTriggerListener, KeyMatcher.keyEquals(triggerKey));
    }

    public static void setupDDPConfigurationLookup(@NonNull String ddpConf) {
        JsonArray array = (JsonArray) (new JsonParser().parse(ddpConf));
        for (JsonElement ddpInfo : array) {
            if (ddpInfo.isJsonObject()) {
                ddpConfigurationLookup.put(
                        ddpInfo.getAsJsonObject().get(ApplicationConfigConstants.INSTANCE_NAME).getAsString().toLowerCase(), ddpInfo);
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
                ddpConfigurationLookup.put(
                        ddpInfo.getAsJsonObject().get(ApplicationConfigConstants.SHIPPER_NAME).getAsString().toLowerCase(), ddpInfo);
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
            response.header("Access-Control-Allow-Origin",
                    (StringUtils.isNotBlank(origin) && allowedOrigins.contains(origin)) ? origin : "");
            response.header("Access-Control-Request-Method", methods);
            response.header("Access-Control-Allow-Headers", headers);
            response.header("Access-Control-Allow-Credentials", "true");
            response.type("application/json");
        });
    }

    protected void configureServer(@NonNull Config config) {
        String env = config.getString("portal.environment");
        logger.info("Property source: {} ", env);

        setupDB(config);

        // don't run superclass routing--it won't work with JettyConfig changes for capturing proper IP address in GAE
        setupCustomRouting(config);

        List<String> allowedOrigins = config.getStringList(ApplicationConfigConstants.CORS_ALLOWED_ORIGINS);
        enableCORS(StringUtils.join(allowedOrigins, ","), String.join(",", CORS_HTTP_METHODS), String.join(",", CORS_HTTP_HEADERS));
    }

    protected void setupDB(@NonNull Config config) {
        logger.info("Setup the DB...");

        int maxConnections = config.getInt("portal.maxConnections");
        String dbUrl = config.getString("portal.dbUrl");

        //setup the mysql transaction/connection utility
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.DSM, maxConnections, dbUrl));

        try {
            TransactionWrapper.useTxn(handle -> LiquibaseUtil.logDatabaseChangeLogLocks(handle.getConnection()));
        } catch (SQLException e) {
            throw new DsmInternalError("Could not query liquibase locks", e);
        }

        logger.info("Running DB update...");

        LiquibaseUtil.runLiquibase(dbUrl, TransactionWrapper.DB.DSM);
        LiquibaseUtil.releaseResources();

        logger.info("DB setup complete.");
    }

    protected void setupCustomRouting(@NonNull Config cfg) {
        logger.info("Setup DSM custom routes...");

        //BSP route
        String bspSecret = cfg.getString(ApplicationConfigConstants.BSP_SECRET);
        boolean bspSecretEncoded =
                cfg.hasPath(ApplicationConfigConstants.BSP_ENCODED) && cfg.getBoolean(ApplicationConfigConstants.BSP_ENCODED);

        String dsmSecretForJuniper = cfg.getString(ApplicationConfigConstants.JUNIPER_SECRET);
        String juniperSigner = cfg.getString(ApplicationConfigConstants.JUNIPER_SIGNER);
        boolean juniperSecretEncoded =
                cfg.hasPath(ApplicationConfigConstants.BSP_ENCODED) && cfg.getBoolean(ApplicationConfigConstants.BSP_ENCODED);
        String ddpSecret = cfg.getString(ApplicationConfigConstants.DDP_SECRET);
        boolean ddpSecretEncoded =
                cfg.hasPath(ApplicationConfigConstants.DDP_ENCODED) && cfg.getBoolean(ApplicationConfigConstants.DDP_ENCODED);
        String auth0Domain = cfg.getString(ApplicationConfigConstants.AUTH0_DOMAIN);
        String auth0claimNameSpace = cfg.getString(ApplicationConfigConstants.AUTH0_CLAIM_NAMESPACE);

        if (StringUtils.isBlank(bspSecret)) {
            throw new DsmInternalError("No secret supplied for BSP endpoint, system exiting.");
        }

        if (StringUtils.isBlank(ddpSecret)) {
            throw new DsmInternalError("No secret supplied for DDP endpoint, system exiting.");
        }

        String appRoute = cfg.hasPath("portal.appRoute") ? cfg.getString("portal.appRoute") : null;

        if (StringUtils.isBlank(appRoute)) {
            throw new DsmInternalError("appRoute was not configured correctly.");
        }

        before(API_ROOT + "*", new LoggingFilter(auth0Domain, auth0claimNameSpace, bspSecret, null, bspSecretEncoded));
        before(DSM_ROOT + "*",
                new LoggingFilter(auth0Domain, auth0claimNameSpace, dsmSecretForJuniper, juniperSigner, juniperSecretEncoded));
        before(UI_ROOT + "*", new LoggingFilter(auth0Domain, auth0claimNameSpace, null, null, false));
        before(INFO_ROOT + "*", new LoggingFilter(auth0Domain, auth0claimNameSpace, ddpSecret, KDUX_SIGNER, ddpSecretEncoded));
        before(appRoute + "*", new LoggingFilter(auth0Domain, auth0claimNameSpace, ddpSecret, KDUX_SIGNER, ddpSecretEncoded));
        afterAfter((req, res) -> MDC.clear());

        before(API_ROOT + "*", (req, res) -> {
            if (!new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret)) {
                logger.info("Returning 401 because token was not verified");
                halt(401);
            }
            res.header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        });
        // we are using dsm/ path for calls from Juniper to create a new kit, or to get a kit status
        // these endpoints can technically be used by other users as well as Juniper, but in this point in
        // DSM's lifetime we don't think this would happen, but if needed this implementation should change
        before(DSM_ROOT + "*", (req, res) -> {
            if (!new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, dsmSecretForJuniper)) {
                logger.info("Returning 401 because token was not verified");
                halt(401, "Token could not be verified");
            }
            res.header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        });

        //DSM internal routes
        NotificationUtil notificationUtil = new NotificationUtil(cfg);

        setupPubSub(cfg, notificationUtil);

        get(API_ROOT + RoutePath.BSP_KIT_QUERY_PATH, new BSPKitRoute(notificationUtil), new GsonResponseTransformer());
        get(API_ROOT + RoutePath.BSP_KIT_REGISTERED, new BSPKitRegisteredRoute(), new GsonResponseTransformer());
        get(API_ROOT + RoutePath.CLINICAL_KIT_ENDPOINT, new ClinicalKitsRoute(notificationUtil), new GsonResponseTransformer());

        //Juniper routes
        post(DSM_ROOT + RoutePath.SHIP_KIT_ENDPOINT, new JuniperShipKitRoute(), new GsonResponseTransformer());

        StatusKitRoute statusKitRoute = new StatusKitRoute();
        get(DSM_ROOT + RoutePath.KIT_STATUS_ENDPOINT_STUDY, statusKitRoute, new GsonResponseTransformer());
        get(DSM_ROOT + RoutePath.KIT_STATUS_ENDPOINT_JUNIPER_KIT_ID, statusKitRoute, new GsonResponseTransformer());
        get(DSM_ROOT + RoutePath.KIT_STATUS_ENDPOINT_PARTICIPANT_ID, statusKitRoute, new GsonResponseTransformer());
        post(DSM_ROOT + RoutePath.KIT_STATUS_ENDPOINT_KIT_IDS, statusKitRoute, new GsonResponseTransformer());


        if (!cfg.getBoolean(IS_PRODUCTION)) {
            get(API_ROOT + RoutePath.CREATE_CLINICAL_KIT_ENDPOINT, new CreateClinicalDummyKitRoute(new OncHistoryDetailDaoImpl()),
                    new GsonResponseTransformer());

            get(API_ROOT + RoutePath.CREATE_CLINICAL_KIT_ENDPOINT_WITH_PARTICIPANT, new CreateClinicalDummyKitRoute(
                    new OncHistoryDetailDaoImpl()), new GsonResponseTransformer());
            get(API_ROOT + RoutePath.DUMMY_ENDPOINT, new CreateBSPDummyKitRoute(), new GsonResponseTransformer());
        }


        String auth0Signer = cfg.getString(ApplicationConfigConstants.AUTH0_SIGNER);

        SecurityUtil.init(auth0Domain, auth0claimNameSpace, auth0Signer);

        // path is: /app/drugs (this gets the list of display names)
        DrugRoute drugRoute = new DrugRoute();
        get(appRoute + RoutePath.DRUG_LIST_REQUEST, drugRoute, new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.DRUG_LIST_REQUEST, drugRoute, new GsonResponseTransformer());

        CancerRoute cancerRoute = new CancerRoute();
        get(appRoute + RoutePath.CANCER_LIST_REQUEST, cancerRoute, new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.CANCER_LIST_REQUEST, cancerRoute, new GsonResponseTransformer());

        auth0Util = new Auth0Util(cfg.getString(ApplicationConfigConstants.AUTH0_ACCOUNT),
                cfg.getStringList(ApplicationConfigConstants.AUTH0_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.AUTH0_CLIENT_KEY), cfg.getString(ApplicationConfigConstants.AUTH0_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_KEY), cfg.getString(ApplicationConfigConstants.AUTH0_MGT_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_API_URL), cfg.getString(ApplicationConfigConstants.AUTH0_AUDIENCE));

        before(INFO_ROOT + RoutePath.PARTICIPANT_STATUS_REQUEST, (req, res) -> {
            String tokenFromHeader = Utility.getTokenFromHeader(req);
            Auth0Util.verifyAuth0Token(tokenFromHeader, cfg.getString(ApplicationConfigConstants.AUTH0_DOMAIN), ddpSecret, KDUX_SIGNER,
                    ddpSecretEncoded);
        });

        get(INFO_ROOT + RoutePath.PARTICIPANT_STATUS_REQUEST, new ParticipantStatusRoute(), new JsonNullTransformer());

        // requests from frontend
        before(UI_ROOT + "*", (req, res) -> {
            if (!"OPTIONS".equals(req.requestMethod()) && !req.pathInfo().contains(RoutePath.AUTHENTICATION_REQUEST)) {
                String tokenFromHeader = Utility.getTokenFromHeader(req);

                boolean isTokenValid = false;
                if (StringUtils.isNotBlank(tokenFromHeader)) {
                    isTokenValid = new JWTRouteFilter(auth0Domain).isAccessAllowed(req, true, null);
                }
                if (!isTokenValid) {
                    halt(401, SecurityUtil.ResultType.AUTHENTICATION_ERROR.toString());
                }
            }
        });
        setupDDPConfigurationLookup(cfg.getString(ApplicationConfigConstants.DDP));

        AuthenticationRoute authenticationRoute = new AuthenticationRoute(auth0Util,
                cfg.getString(ApplicationConfigConstants.AUTH0_DOMAIN),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_KEY),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_API_URL),
                cfg.getString(ApplicationConfigConstants.AUTH0_CLAIM_NAMESPACE)
        );
        post(UI_ROOT + RoutePath.AUTHENTICATION_REQUEST, authenticationRoute, new GsonResponseTransformer());

        KitUtil kitUtil = new KitUtil();

        PatchUtil patchUtil = new PatchUtil();

        setupExternalShipperLookup(cfg.getString(ApplicationConfigConstants.EXTERNAL_SHIPPER));
        //        GBFRequestUtil gbfRequestUtil = new GBFRequestUtil();

        setupShippingRoutes(notificationUtil, auth0Util, cfg.getString(ApplicationConfigConstants.AUTH0_DOMAIN));

        setupMedicalRecordRoutes(cfg, notificationUtil, patchUtil);

        setupMRAbstractionRoutes();

        setupMiscellaneousRoutes();

        setupAdminRoutes();

        setupSharedRoutes(kitUtil, notificationUtil, patchUtil);

        setupCohortTagRoutes();

        setupPubSubPublisherRoutes(cfg);

        setupRouteGenericErrorHandlers();

        setupSomaticUploadRoutes(cfg);

        //no GET for USER_SETTINGS_REQUEST because UI gets them per AuthenticationRoute
        patch(UI_ROOT + RoutePath.USER_SETTINGS_REQUEST, new UserSettingRoute(), new GsonResponseTransformer());

        EventService eventService = new EventService();
        scheduler = setupJobs(cfg, kitUtil, notificationUtil, eventService);

        //TODO - redo with pubsub
        JavaHeapDumper heapDumper = new JavaHeapDumper();
        get(UI_ROOT + "/heapDump", new Route() {
            @Override
            public Object handle(Request request, Response response) throws Exception {
                logger.info("Received request to create java heap dump");
                String gcpName = DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME);
                heapDumper.dumpHeapToBucket(gcpName + "_dsm_heapdumps");
                return null;
            }
        }, new GsonResponseTransformer());
        logger.info("Finished setting up DSM custom routes and jobs...");
    }

    private void setupCohortTagRoutes() {
        post(UI_ROOT + RoutePath.CREATE_COHORT_TAG, new CreateCohortTagRoute(), new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.BULK_CREATE_COHORT_TAGS, new BulkCreateCohortTagRoute(), new GsonResponseTransformer());
        delete(UI_ROOT + RoutePath.DELETE_COHORT_TAG, new DeleteCohortTagRoute(), new GsonResponseTransformer());
    }

    private void setupPubSub(@NonNull Config cfg, NotificationUtil notificationUtil) {
        String projectId = cfg.getString(GCP_PATH_PUBSUB_PROJECT_ID);
        String dsmToDssSubscriptionId = cfg.getString(GCP_PATH_DSM_DSS_SUB);
        String dsmTasksSubscriptionId = cfg.getString(GCP_PATH_DSM_TASKS_SUB);
        String mercuryDsmSubscriptionId = cfg.getString(GCP_PATH_MERCURY_DSM_SUB);
        String antivirusDsmSubscriptionId = cfg.getString(GCP_PATH_ANTI_VIRUS_SUB);

        logger.info("Setting up pubsub for {}/{}", projectId, dsmToDssSubscriptionId);

        try {
            dssSubsciber = PubSubResultMessageSubscription.dssToDsmSubscriber(projectId, dsmToDssSubscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            dsmTasksSubscriber = DSMtasksSubscription.subscribeDSMtasks(projectId, dsmTasksSubscriptionId);
        } catch (Exception e) {
            throw new DsmInternalError("Error initializing DSMtasksSubscription", e);
        }

        try {
            mercuryOrderSubscriber = MercuryOrderStatusListener.subscribeToOrderStatus(projectId, mercuryDsmSubscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            logger.info("Setting up pupsub for somatic antivirus scanning {}/{}", projectId, antivirusDsmSubscriptionId);
            antivirusSubscriber = AntivirusScanningStatusListener.subscribeToAntiVirusStatus(projectId, antivirusDsmSubscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("Pubsub setup complete");
    }

    private void setupShippingRoutes(@NonNull NotificationUtil notificationUtil, @NonNull Auth0Util auth0Util,
                                     @NonNull String auth0Domain) {
        get(UI_ROOT + RoutePath.KIT_REQUESTS_PATH, new KitRequestRoute(), new GsonResponseTransformer());

        post(UI_ROOT + RoutePath.FINAL_SCAN_REQUEST, new KitFinalScanRoute(), new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.RGP_FINAL_SCAN_REQUEST, new RGPKitFinalScanRoute(), new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.TRACKING_SCAN_REQUEST, new KitTrackingScanRoute(), new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.SENT_KIT_REQUEST, new SentKitRoute(), new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.RECEIVED_KIT_REQUEST, new ReceivedKitsRoute(notificationUtil), new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.INITIAL_SCAN_REQUEST, new KitInitialScanRoute(), new GsonResponseTransformer());

        KitDeactivationRoute kitDeactivationRoute = new KitDeactivationRoute(notificationUtil);
        patch(UI_ROOT + RoutePath.DEACTIVATE_KIT_REQUEST, kitDeactivationRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.ACTIVATE_KIT_REQUEST, kitDeactivationRoute, new GsonResponseTransformer());

        patch(UI_ROOT + RoutePath.AUTHORIZE_KIT, new KitAuthorizationRoute(), new GsonResponseTransformer());

        KitExpressRoute kitExpressRoute = new KitExpressRoute(notificationUtil);
        get(UI_ROOT + RoutePath.EXPRESS_KIT_REQUEST, kitExpressRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.EXPRESS_KIT_REQUEST, kitExpressRoute, new GsonResponseTransformer());

        LabelSettingRoute labelSettingRoute = new LabelSettingRoute();
        get(UI_ROOT + RoutePath.LABEL_SETTING_REQUEST, labelSettingRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.LABEL_SETTING_REQUEST, labelSettingRoute, new GsonResponseTransformer());

        post(UI_ROOT + RoutePath.KIT_UPLOAD_REQUEST, new KitUploadRoute(notificationUtil), new GsonResponseTransformer());

        KitLabelRoute kitLabelRoute = new KitLabelRoute();
        get(UI_ROOT + RoutePath.KIT_LABEL_REQUEST, kitLabelRoute, new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.KIT_LABEL_REQUEST, kitLabelRoute, new GsonResponseTransformer());

        get(UI_ROOT + RoutePath.SEARCH_KIT, new KitSearchRoute(), new GsonResponseTransformer());

        KitDiscardRoute kitDiscardRoute = new KitDiscardRoute(auth0Util, auth0Domain);
        get(UI_ROOT + RoutePath.DISCARD_SAMPLES, kitDiscardRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.DISCARD_SAMPLES, kitDiscardRoute, new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.DISCARD_UPLOAD, kitDiscardRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.DISCARD_SHOW_UPLOAD, kitDiscardRoute, new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.DISCARD_CONFIRM, kitDiscardRoute, new GsonResponseTransformer());
    }

    //Routes used by medical record
    private void setupMedicalRecordRoutes(@NonNull Config cfg, @NonNull NotificationUtil notificationUtil, @NonNull PatchUtil patchUtil) {
        //Medical Record
        get(UI_ROOT + RoutePath.ASSIGNEE_REQUEST, new AssigneeRoute(), new GsonResponseTransformer());

        InstitutionRoute institutionRoute = new InstitutionRoute();
        post(UI_ROOT + RoutePath.INSTITUTION_REQUEST, institutionRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.INSTITUTION_REQUEST, institutionRoute, new GsonResponseTransformer());

        DownloadPDFRoute pdfRoute = new DownloadPDFRoute();
        post(UI_ROOT + RoutePath.DOWNLOAD_PDF + DownloadPDFRoute.PDF, pdfRoute, new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.DOWNLOAD_PDF + DownloadPDFRoute.BUNDLE, pdfRoute, new GsonResponseTransformer());
        get(UI_ROOT + DownloadPDFRoute.PDF, pdfRoute, new GsonResponseTransformer());

        patch(UI_ROOT + RoutePath.ASSIGN_PARTICIPANT_REQUEST,
                new AssignParticipantRoute(cfg.getString(ApplicationConfigConstants.GET_DDP_PARTICIPANT_ID),
                        cfg.getString(ApplicationConfigConstants.EMAIL_FRONTEND_URL_FOR_LINKS), notificationUtil),
                new GsonResponseTransformer());

        ViewFilterRoute viewFilterRoute = new ViewFilterRoute(patchUtil);
        //gets filter names for user for this realm (shared filters and user's filters
        get(UI_ROOT + RoutePath.GET_FILTERS, viewFilterRoute, new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.GET_DEFAULT_FILTERS, viewFilterRoute, new GsonResponseTransformer());
        //saves the current Filter Parameters with a name for future use
        patch(UI_ROOT + RoutePath.SAVE_FILTER, viewFilterRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.FILTER_DEFAULT, viewFilterRoute, new GsonResponseTransformer());

        FilterRoute filterRoute = new FilterRoute();
        //returns List[] that is filtered based on the filterName
        get(UI_ROOT + RoutePath.APPLY_FILTER, filterRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.FILTER_LIST, filterRoute, new GsonResponseTransformer());
        //gets the participant to go to the tissue that was clicked on
        get(UI_ROOT + RoutePath.GET_PARTICIPANT, new GetParticipantRoute(), new GsonResponseTransformer());

        MedicalRecordLogRoute medicalRecordLogRoute = new MedicalRecordLogRoute();
        get(UI_ROOT + RoutePath.MEDICAL_RECORD_LOG_REQUEST, medicalRecordLogRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.MEDICAL_RECORD_LOG_REQUEST, medicalRecordLogRoute, new GsonResponseTransformer());

        get(UI_ROOT + RoutePath.LOOKUP, new LookupRoute(), new GsonResponseTransformer());

        FieldSettingsRoute fieldSettingsRoute = new FieldSettingsRoute();
        get(UI_ROOT + RoutePath.FIELD_SETTINGS_ROUTE, fieldSettingsRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.FIELD_SETTINGS_ROUTE, fieldSettingsRoute, new GsonResponseTransformer());

        get(UI_ROOT + RoutePath.DISPLAY_SETTINGS_ROUTE, new DisplaySettingsRoute(patchUtil), new GsonResponseTransformer());

        post(UI_ROOT + RoutePath.DOWNLOAD_PARTICIPANT_LIST_ROUTE, new DownloadParticipantListRoute());

        post(UI_ROOT + RoutePath.ONC_HISTORY_ROUTE, new OncHistoryUploadRoute(), new GsonResponseTransformer());

        get(UI_ROOT + RoutePath.ONC_HISTORY_TEMPLATE_ROUTE, new OncHistoryTemplateRoute());
    }

    private void setupMRAbstractionRoutes() {
        AbstractionFormControlRoute abstractionFormControlRoute = new AbstractionFormControlRoute();
        get(UI_ROOT + RoutePath.ABSTRACTION_FORM_CONTROLS, abstractionFormControlRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.ABSTRACTION_FORM_CONTROLS, abstractionFormControlRoute, new GsonResponseTransformer());

        post(UI_ROOT + RoutePath.ABSTRACTION, new AbstractionRoute(), new GsonResponseTransformer());
    }

    private void setupMiscellaneousRoutes() {
        get(UI_ROOT + RoutePath.PHI_MANIFEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, new PhiManifestReportRoute(),
                new GsonResponseTransformer());
        MailingListRoute mailingListRoute = new MailingListRoute();
        get(UI_ROOT + RoutePath.MAILING_LIST_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, mailingListRoute,
                new GsonResponseTransformer());

        ParticipantExitRoute participantExitRoute = new ParticipantExitRoute();
        get(UI_ROOT + RoutePath.PARTICIPANT_EXIT_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, participantExitRoute,
                new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.PARTICIPANT_EXIT_REQUEST, participantExitRoute, new GsonResponseTransformer());

        TriggerSurveyRoute triggerSurveyRoute = new TriggerSurveyRoute();
        get(UI_ROOT + RoutePath.TRIGGER_SURVEY + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, triggerSurveyRoute,
                new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.TRIGGER_SURVEY, triggerSurveyRoute, new GsonResponseTransformer());

        get(UI_ROOT + RoutePath.EVENT_TYPES + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, new EventTypeRoute(),
                new GsonResponseTransformer());

        SkippedParticipantEventRoute skippedParticipantEventRoute = new SkippedParticipantEventRoute();
        get(UI_ROOT + RoutePath.PARTICIPANT_EVENTS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, skippedParticipantEventRoute,
                new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.SKIP_PARTICIPANT_EVENTS, skippedParticipantEventRoute, new GsonResponseTransformer());

        post(UI_ROOT + RoutePath.NDI_REQUEST, new NDIRoute(), new GsonResponseTransformer());

        DrugListRoute drugListRoute = new DrugListRoute();
        get(UI_ROOT + RoutePath.FULL_DRUG_LIST_REQUEST, drugListRoute, new GsonResponseTransformer());
        patch(UI_ROOT + RoutePath.FULL_DRUG_LIST_REQUEST, drugListRoute, new GsonResponseTransformer());

        AddFamilyMemberRoute addFamilyMemberRoute = new AddFamilyMemberRoute();
        post(UI_ROOT + RoutePath.ADD_FAMILY_MEMBER, addFamilyMemberRoute, new GsonResponseTransformer());

        GetParticipantDataRoute getParticipantDataRoute = new GetParticipantDataRoute();
        get(UI_ROOT + RoutePath.GET_PARTICIPANT_DATA, getParticipantDataRoute, new GsonResponseTransformer());
    }

    private void setupAdminRoutes() {
        StudyRoleRoute studyRoleRoute = new StudyRoleRoute();
        get(UI_ROOT + RoutePath.STUDY_ROLE, studyRoleRoute, new GsonResponseTransformer());

        UserRoleRoute userRoleRoute = new UserRoleRoute();
        get(UI_ROOT + RoutePath.USER_ROLE, userRoleRoute, new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.USER_ROLE, userRoleRoute, new GsonResponseTransformer());
        put(UI_ROOT + RoutePath.USER_ROLE, userRoleRoute, new GsonResponseTransformer());

        UserRoute userRoute = new UserRoute();
        post(UI_ROOT + RoutePath.USER, userRoute, new GsonResponseTransformer());
        put(UI_ROOT + RoutePath.USER, userRoute, new GsonResponseTransformer());

        AdminOperationRoute adminOperationRoute = new AdminOperationRoute();
        post(UI_ROOT + RoutePath.ADMIN_OPERATION, adminOperationRoute, new JacksonResponseTransformer());
        get(UI_ROOT + RoutePath.ADMIN_OPERATION, adminOperationRoute, new JacksonResponseTransformer());
    }


    private void setupSomaticUploadRoutes(@NonNull Config cfg) {
        SomaticResultUploadService somaticResultUploadService = SomaticResultUploadService.fromConfig(cfg);
        post(UI_ROOT + RoutePath.SOMATIC_DOCUMENT_ROUTE,
                new PostSomaticResultUploadRoute(somaticResultUploadService), new GsonResponseTransformer());
        delete(UI_ROOT + RoutePath.SOMATIC_DOCUMENT_ROUTE, new DeleteSomaticResultRoute(somaticResultUploadService),
                new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.SOMATIC_DOCUMENT_ROUTE,
                new GetSomaticResultsRoute(somaticResultUploadService), new GsonResponseTransformer());
        post(UI_ROOT + RoutePath.TRIGGER_SOMATIC_SURVEY,
                new TriggerSomaticResultSurveyRoute(somaticResultUploadService), new GsonResponseTransformer());
    }

    private void setupSharedRoutes(@NonNull KitUtil kitUtil, @NonNull NotificationUtil notificationUtil, @NonNull PatchUtil patchUtil) {
        DashboardRoute dashboardRoute = new DashboardRoute(kitUtil);
        get(UI_ROOT + RoutePath.DASHBOARD_REQUEST, dashboardRoute, new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.DASHBOARD_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.START + RoutePath.ROUTE_SEPARATOR
                + RequestParameter.END, dashboardRoute, new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.SAMPLE_REPORT_REQUEST, dashboardRoute, new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.SAMPLE_REPORT_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.START + RoutePath.ROUTE_SEPARATOR
                + RequestParameter.END, dashboardRoute, new GsonResponseTransformer());

        AllowedRealmsRoute allowedRealmsRoute = new AllowedRealmsRoute();
        get(UI_ROOT + RoutePath.ALLOWED_REALMS_REQUEST, allowedRealmsRoute, new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.STUDIES, allowedRealmsRoute, new GsonResponseTransformer());

        KitTypeRoute kitTypeRoute = new KitTypeRoute(kitUtil);
        get(UI_ROOT + RoutePath.KIT_TYPES_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, kitTypeRoute,
                new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.UPLOAD_REASONS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, kitTypeRoute,
                new GsonResponseTransformer());
        get(UI_ROOT + RoutePath.CARRIERS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, new CarrierServiceRoute(),
                new GsonResponseTransformer());

        patch(UI_ROOT + RoutePath.PATCH, new PatchRoute(notificationUtil, patchUtil), new GsonResponseTransformer());

        get(UI_ROOT + RoutePath.DASHBOARD, new NewDashboardRoute(), new GsonResponseTransformer());
    }

    private void setupPubSubPublisherRoutes(Config config) {
        String projectId = config.getString(GCP_PATH_PUBSUB_PROJECT_ID);
        String dsmToDssTopicId = config.getString(GCP_PATH_DSM_DSS_TOPIC);

        EditParticipantPublisherRoute editParticipantPublisherRoute = new EditParticipantPublisherRoute(projectId, dsmToDssTopicId);
        put(UI_ROOT + RoutePath.EDIT_PARTICIPANT, editParticipantPublisherRoute, new GsonResponseTransformer());

        EditParticipantMessageReceiverRoute editParticipantMessageReceiverRoute = new EditParticipantMessageReceiverRoute();
        get(UI_ROOT + RoutePath.EDIT_PARTICIPANT_MESSAGE, editParticipantMessageReceiverRoute, new GsonResponseTransformer());

        String mercuryTopicId = config.getString(GCP_PATH_DSM_MERCURY_TOPIC);
        if (!config.getBoolean(IS_PRODUCTION)) {
            PostMercuryOrderDummyRoute postMercuryOrderDummyRoute = new PostMercuryOrderDummyRoute(projectId, mercuryTopicId);
            post(API_ROOT + RoutePath.SUBMIT_MERCURY_ORDER, postMercuryOrderDummyRoute, new GsonResponseTransformer());
        }

        FileDownloadService fileDownloadService = FileDownloadService.fromConfig(config);
        get(UI_ROOT + RoutePath.DOWNLOAD_PARTICIPANT_FILE, new DownloadParticipantFileRoute(fileDownloadService),
                new GsonResponseTransformer());

        post(UI_ROOT + RoutePath.SUBMIT_MERCURY_ORDER, new PostMercuryOrderRoute(projectId, mercuryTopicId), new GsonResponseTransformer());

        GetMercuryEligibleSamplesRoute getMercuryEligibleSamplesRoute = new GetMercuryEligibleSamplesRoute(
                new MercurySampleDao(), projectId, mercuryTopicId, new KitDao());
        get(UI_ROOT + RoutePath.MERCURY_SAMPLES_ROUTE, getMercuryEligibleSamplesRoute, new GsonResponseTransformer());

        GetMercuryOrdersRoute getMercuryOrdersRoute = new GetMercuryOrdersRoute(
                new MercurySampleDao(), new ClinicalOrderDao(), projectId, mercuryTopicId);
        get(UI_ROOT + RoutePath.GET_MERCURY_ORDERS_ROUTE, getMercuryOrdersRoute, new GsonResponseTransformer());

    }

    private Scheduler setupJobs(@NonNull Config cfg, @NonNull KitUtil kitUtil, @NonNull NotificationUtil notificationUtil,
                                @NonNull EventService eventService) {
        String schedulerName = null;
        Scheduler scheduler = null;
        if (cfg.getBoolean(ApplicationConfigConstants.QUARTZ_ENABLE_JOBS)) {
            logger.info("Setting up jobs");
            try {
                scheduler = new StdSchedulerFactory().getScheduler();
                schedulerName = scheduler.getSchedulerName();
                createDDPRequestScheduledJobs(scheduler, DDPRequestJob.class, "DDPREQUEST_JOB",
                        cfg.getInt(ApplicationConfigConstants.QUARTZ_DDP_REQUEST_JOB_INTERVAL_SEC), new DDPRequestTriggerListener(),
                        notificationUtil);

                createScheduledJob(scheduler, cfg, NotificationJob.class, "NOTIFICATION_JOB",
                        cfg.getInt(ApplicationConfigConstants.QUARTZ_NOTIFICATION_JOB_INTERVAL_SEC), new NotificationTriggerListener());

                createScheduledJob(scheduler, cfg, LabelCreationJob.class, "LABEL_CREATION_JOB",
                        cfg.getInt(ApplicationConfigConstants.QUARTZ_LABEL_CREATION_JOB_INTERVAL_SEC), new LabelCreationTriggerListener());

                createScheduleJob(scheduler, cfg, notificationUtil, kitUtil, GPNotificationJob.class, "GP_SCHEDULE_JOB",
                        cfg.getString(ApplicationConfigConstants.EMAIL_CRON_EXPRESSION_FOR_GP_NOTIFICATION));

                createScheduleJob(scheduler, eventService, notificationUtil, DDPEventJob.class, "TRIGGER_DDP_EVENT",
                        cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_EXPRESSION_FOR_DDP_EVENT_TRIGGER),
                        new DDPEventTriggerListener());

                // currently not needed anymore but might come back
                // createScheduleJob(scheduler, eventUtil, notificationUtil, ExternalShipperJob.class, "CHECK_EXTERNAL_SHIPPER",
                // cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_EXPRESSION_FOR_EXTERNAL_SHIPPER),
                // new ExternalShipperTriggerListener(), cfg);

                createScheduleJob(scheduler, null, null, EasypostShipmentStatusJob.class, "CHECK_STATUS_SHIPMENT",
                        cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_STATUS_SHIPMENT), new EasypostShipmentStatusTriggerListener());
                startScheduler(scheduler);
            } catch (Exception e) {
                throw new DsmInternalError("Could not create scheduler ", e);
            }

        }
        return scheduler;
    }

    private void startScheduler(Scheduler scheduler) throws SchedulerException {
        logger.info("Setup Job Scheduler...");
        scheduler.start();
        logger.info("Job Scheduler setup complete.");

    }

    private void setupRouteGenericErrorHandlers() {
        exception(DSMBadRequestException.class, (exception, request, response) -> {
            logger.info("Request error while processing request: {}: {}", request.url(), exception.toString());
            response.status(400);
            response.body(exception.getMessage());
        });
        exception(DsmInternalError.class, (exception, request, response) -> {
            logger.error("Internal error while processing request: {}: {}", request.url(), exception.toString());
            exception.printStackTrace();
            response.status(500);
            response.body(exception.getMessage());
        });
        // todo not sure if this will remain in this ticket or not
        exception(UnsafeDeleteError.class, (exception, request, response) -> {
            logger.warn("DSM is unable to delete the object {}",  exception.toString());
            response.status(500);
            response.body(exception.getMessage());
        });
        exception(DDPInternalError.class, (exception, request, response) -> {
            logger.error("Internal error while processing request: {}: {}", request.url(), exception.toString());
            exception.printStackTrace();
            response.status(500);
            response.body(exception.getMessage());
        });
        exception(AuthorizationException.class, (exception, request, response) -> {
            logger.info("Authorization error while processing request: {}: {}", request.url(), exception.toString());
            response.status(403);
            response.body(exception.getMessage());
        });
        exception(TokenExpiredException.class, (exception, request, response) -> {
            logger.info("Token expiration while processing request: {}: {}", request.url(), exception.toString());
            response.status(401);
            response.body(exception.getMessage());
        });
        exception(InvalidTokenException.class, (exception, request, response) -> {
            logger.info("Invalid token while processing request: {}: {}", request.url(), exception.toString());
            response.status(401);
            response.body(exception.getMessage());
        });
        exception(AuthenticationException.class, (exception, request, response) -> {
            // this is a fallback exception, log it warn level to see why it is happening
            logger.warn("Authentication error while processing request: {}: {}", request.url(), exception.toString());
            response.status(401);
            response.body(exception.getMessage());
        });
        exception(EntityNotFound.class, (exception, request, response) -> {
            logger.info("Entity not found while processing request: {}: {}", request.url(), exception.toString());
            response.status(404);
            response.body(exception.getMessage());
        });
        exception(DuplicateEntityException.class, (e, request, response) -> {
            // Tell the user to retry with a different name.  Not logged as an error,
            // since this can happen during normal operations.
            response.status(422);
            response.body(String.format("%s %s is already taken.  Please retry with a different %s.",
                    e.getEntityName(), e.getEntityValue(), e.getEntityName()));
        });
    }

    public static void shutdown() {
        logger.info("Shutting down DSM instance {}", LogUtil.getAppEngineInstance());
        // shutdown jobs
        if (scheduler != null) {
            logger.info("Shutting down quartz.");
            try {
                if (!scheduler.isShutdown()) {
                    scheduler.shutdown();
                }
            } catch (SchedulerException e) {
                logger.error("Error shutting down quartz.", e);
            }
        }

        // shutdown pubsub
        logger.info("Shutting down pubsub.");
        if (dssSubsciber != null) {
            if (dssSubsciber.isRunning()) {
                dssSubsciber.stopAsync();
            }
        }
        if (mercuryOrderSubscriber != null) {
            if (mercuryOrderSubscriber.isRunning()) {
                mercuryOrderSubscriber.stopAsync();
            }
        }
        if (dsmTasksSubscriber != null) {
            if (dsmTasksSubscriber.isRunning()) {
                dsmTasksSubscriber.stopAsync();
            }
        }
        if (antivirusSubscriber != null) {
            if (antivirusSubscriber.isRunning()) {
                antivirusSubscriber.stopAsync();
            }
        }

        // shutdown db pool
        logger.info("Shutting down db pool");
        TransactionWrapper.reset();

        // shutdown spark
        Spark.stop();
    }
}
