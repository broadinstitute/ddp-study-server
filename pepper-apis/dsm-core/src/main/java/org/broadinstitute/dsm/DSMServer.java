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
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.put;
import static spark.Spark.threadPool;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.auth0.jwt.exceptions.TokenExpiredException;
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
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPInternalError;
import org.broadinstitute.ddp.util.LiquibaseUtil;

import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.kit.KitDaoImpl;
import org.broadinstitute.dsm.db.dao.mercury.ClinicalOrderDao;
import org.broadinstitute.dsm.db.dao.mercury.MercurySampleDao;
import org.broadinstitute.dsm.exception.AuthenticationException;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.jetty.JettyConfig;
import org.broadinstitute.dsm.jobs.DDPEventJob;
import org.broadinstitute.dsm.jobs.DDPRequestJob;
import org.broadinstitute.dsm.jobs.EasypostShipmentStatusJob;
import org.broadinstitute.dsm.jobs.GPNotificationJob;
import org.broadinstitute.dsm.jobs.LabelCreationJob;
import org.broadinstitute.dsm.jobs.NotificationJob;
import org.broadinstitute.dsm.jobs.PubSubLookUp;
import org.broadinstitute.dsm.log.SlackAppender;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperKitCreationService;
import org.broadinstitute.dsm.model.nonpepperkit.NonPepperStatusKitService;
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
import org.broadinstitute.dsm.route.ParticipantEventRoute;
import org.broadinstitute.dsm.route.ParticipantExitRoute;
import org.broadinstitute.dsm.route.ParticipantStatusRoute;
import org.broadinstitute.dsm.route.PatchRoute;
import org.broadinstitute.dsm.route.TriggerSomaticResultSurveyRoute;
import org.broadinstitute.dsm.route.TriggerSurveyRoute;
import org.broadinstitute.dsm.route.UserSettingRoute;
import org.broadinstitute.dsm.route.ViewFilterRoute;
import org.broadinstitute.dsm.route.admin.RegisterParticipantRoute;
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
import org.broadinstitute.dsm.route.somaticresults.DeleteSomaticResultRoute;
import org.broadinstitute.dsm.route.somaticresults.GetSomaticResultsRoute;
import org.broadinstitute.dsm.route.somaticresults.PostSomaticResultUploadRoute;
import org.broadinstitute.dsm.route.tag.cohort.BulkCreateCohortTagRoute;
import org.broadinstitute.dsm.route.tag.cohort.CreateCohortTagRoute;
import org.broadinstitute.dsm.route.tag.cohort.DeleteCohortTagRoute;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.dsm.service.FileDownloadService;
import org.broadinstitute.dsm.service.SomaticResultUploadService;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.JWTRouteFilter;
import org.broadinstitute.dsm.util.JavaHeapDumper;
import org.broadinstitute.dsm.util.JsonNullTransformer;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.SecurityUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.triggerlistener.DDPEventTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.DDPRequestTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.EasypostShipmentStatusTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.GPNotificationTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.LabelCreationTriggerListener;
import org.broadinstitute.dsm.util.triggerlistener.NotificationTriggerListener;
import org.broadinstitute.lddp.exception.InvalidTokenException;
import org.broadinstitute.lddp.util.BasicTriggerListener;
import org.broadinstitute.lddp.util.JsonTransformer;
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

    private static final Logger logger = LoggerFactory.getLogger(DSMServer.class);
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
    private static final String gcpPathToPubsubProjectId = "pubsub.projectId";
    private static final String gcpPathToPubsubSub = "pubsub.subscription";
    private static final String gcpPathToDssToDsmSub = "pubsub.dss_to_dsm_subscription";
    private static final String gcpPathToDsmToDssTopic = "pubsub.dsm_to_dss_topic";
    private static final String gcpPathToDsmTasksSub = "pubsub.dsm_tasks_subscription";
    public static final String gcpPathToDsmToMercuryTopic = "pubsub.dsm_to_mercury_topic";
    public static final String gcpPathToMercuryToDsmSub = "pubsub.mercury_to_dsm_subscription";
    public static final String gcpPathToAntiVirusSub = "pubsub.antivirus_to_dsm_subscription";
    private static final String apiRoot = "/ddp/";
    private static final String uiRoot = "/ui/";
    private static final String infoRoot = "/info/";
    private static final String signer = "org.broadinstitute.kdux";
    private static final String[] corsHttpMethods = new String[] {"GET", "PUT", "POST", "OPTIONS", "PATCH"};
    private static final String[] corsHttpHeaders =
            new String[] {"Content-Type", "Authorization", "X-Requested-With", "Content-Length", "Accept", "Origin", ""};
    private static final String vaultConf = "vault.conf";
    private static final String gaeDeployDir = "appengine/deploy";
    private static final Duration defaultBootWait = Duration.ofMinutes(10);
    private static Map<String, JsonElement> ddpConfigurationLookup = new HashMap<>();
    private static final AtomicBoolean isReady = new AtomicBoolean(false);
    private static Auth0Util auth0Util;

    public static void main(String[] args) {
        // immediately lock isReady so that ah/start route will wait
        synchronized (isReady) {
            try {
                logger.info("Starting up DSM");
                //config without secrets
                Config cfg = ConfigFactory.load();
                //secrets from vault in a config file
                File vaultConfigInCwd = new File(vaultConf);
                File vaultConfigInDeployDir = new File(gaeDeployDir, vaultConf);
                File vaultConfig = vaultConfigInCwd.exists() ? vaultConfigInCwd : vaultConfigInDeployDir;
                logger.info("Reading config values from " + vaultConfig.getAbsolutePath());
                cfg = cfg.withFallback(ConfigFactory.parseFile(vaultConfig));

                if (cfg.hasPath(GCP_PATH_TO_SERVICE_ACCOUNT)) {
                    if (StringUtils.isNotBlank(cfg.getString("portal.googleProjectCredentials"))) {
                        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", cfg.getString("portal.googleProjectCredentials"));
                    }
                }

                new DSMConfig(cfg);

                String preferredSourceIPHeader = null;
                if (cfg.hasPath(ApplicationConfigConstants.PREFERRED_SOURCE_IP_HEADER)) {
                    preferredSourceIPHeader = cfg.getString(ApplicationConfigConstants.PREFERRED_SOURCE_IP_HEADER);
                }
                JettyConfig.setupJetty(preferredSourceIPHeader);
                DSMServer server = new DSMServer();
                server.configureServer(cfg);
                isReady.set(true);
                logger.info("DSM Startup Complete");
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
        TriggerKey triggerKey = new TriggerKey(identity + "_TRIGGER", "DDP");
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
        TriggerKey triggerKey = new TriggerKey(identity + "_TRIGGER", "DDP");
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
        TriggerKey triggerKey = new TriggerKey(identity + "_TRIGGER", "DDP");
        CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey).withSchedule(cronSchedule(cronExpression)).build();
        //add job
        scheduler.scheduleJob(job, trigger);
        //add listener for all triggers
        scheduler.getListenerManager().addTriggerListener(new GPNotificationTriggerListener(), KeyMatcher.keyEquals(triggerKey));
    }

    /**
     * Job to trigger ddp reminder emails and external shipper
     */
    public static void createScheduleJob(@NonNull Scheduler scheduler, EventUtil eventUtil, NotificationUtil notificationUtil,
                                         @NonNull Class<? extends Job> jobClass, @NonNull String identity, @NonNull String cronExpression,
                                         @NonNull BasicTriggerListener basicTriggerListener, Config config) throws SchedulerException {
        //create job
        JobDetail job = JobBuilder.newJob(jobClass).withIdentity(identity, BasicTriggerListener.NO_CONCURRENCY_GROUP).build();

        if (eventUtil != null) {
            //pass parameters to JobDataMap for JobDetail
            job.getJobDataMap().put(EVENT_UTIL, eventUtil);
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
        TriggerKey triggerKey = new TriggerKey(identity + "_TRIGGER", "DDP");
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

    private static void registerAppEngineStartupCallback(long bootTimeoutSeconds, Config cfg) {
        // Block until isReady is available, with an optional timeout to prevent
        // instance for sitting around too long in a nonresponsive state.  There is a
        // judgement call to be made here to allow for lengthy liquibase migrations during boot.
        logger.info("Will wait for at most {} seconds for boot before GAE termination", bootTimeoutSeconds);
        get("/_ah/start", new ReadinessRoute(bootTimeoutSeconds));

        get(RoutePath.GAE.STOP_ENDPOINT, (request, response) -> {
            logger.info("Received GAE stop request [{}]", RoutePath.GAE.STOP_ENDPOINT);
            //flush out any pending GA events
            response.status(HttpStatus.SC_OK);
            return "";
        });
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
        logger.info("Property source: " + config.getString("portal.environment"));
        logger.info("Configuring the server...");
        threadPool(-1, -1, 60000);
        int port = config.getInt("portal.port");
        String appEnginePort = System.getenv("PORT");

        // if port is passed via env var, assume it's GAE and prefer this port
        if (appEnginePort != null) {
            port = Integer.parseInt(appEnginePort);
        }
        long bootTimeoutSeconds = defaultBootWait.getSeconds();
        if (config.hasPath(ApplicationConfigConstants.BOOT_TIMEOUT)) {
            bootTimeoutSeconds = config.getInt(ApplicationConfigConstants.BOOT_TIMEOUT);
        }

        logger.info("Using port {}", port);
        port(port);

        registerAppEngineStartupCallback(bootTimeoutSeconds, config);

        setupDB(config);

        // don't run superclass routing--it won't work with JettyConfig changes for capturing proper IP address in GAE
        setupCustomRouting(config);

        List<String> allowedOrigins = config.getStringList(ApplicationConfigConstants.CORS_ALLOWED_ORIGINS);
        enableCORS(StringUtils.join(allowedOrigins, ","), String.join(",", corsHttpMethods), String.join(",", corsHttpHeaders));
    }

    protected void setupDB(@NonNull Config config) {
        logger.info("Setup the DB...");

        int maxConnections = config.getInt("portal.maxConnections");
        String dbUrl = config.getString("portal.dbUrl");

        //setup the mysql transaction/connection utility
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.DSM, maxConnections, dbUrl));

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
                cfg.hasPath(ApplicationConfigConstants.BSP_ENCODED) ? cfg.getBoolean(ApplicationConfigConstants.BSP_ENCODED) : false;
        String ddpSecret = cfg.getString(ApplicationConfigConstants.DDP_SECRET);
        boolean ddpSecretEncoded =
                cfg.hasPath(ApplicationConfigConstants.DDP_ENCODED) ? cfg.getBoolean(ApplicationConfigConstants.DDP_ENCODED) : false;
        String auth0Domain = cfg.getString(ApplicationConfigConstants.AUTH0_DOMAIN);
        String auth0claimNameSpace = cfg.getString(ApplicationConfigConstants.AUTH0_CLAIM_NAMESPACE);

        if (StringUtils.isBlank(bspSecret)) {
            throw new RuntimeException("No secret supplied for BSP endpoint, system exiting.");
        }

        if (StringUtils.isBlank(ddpSecret)) {
            throw new RuntimeException("No secret supplied for DDP endpoint, system exiting.");
        }

        String appRoute = cfg.hasPath("portal.appRoute") ? cfg.getString("portal.appRoute") : null;

        if (StringUtils.isBlank(appRoute)) {
            throw new RuntimeException("appRoute was not configured correctly.");
        }

        //  capture basic route info for logging
        //        before("*", new LoggingFilter(auth0Domain, auth0claimNameSpace));
        before(apiRoot + "*", new LoggingFilter(auth0Domain, auth0claimNameSpace, bspSecret, null, bspSecretEncoded));
        before(uiRoot + "*", new LoggingFilter(auth0Domain, auth0claimNameSpace, null, null, false));
        before(infoRoot + "*", new LoggingFilter(auth0Domain, auth0claimNameSpace, ddpSecret, signer, ddpSecretEncoded));
        before(appRoute + "*", new LoggingFilter(auth0Domain, auth0claimNameSpace, ddpSecret, signer, ddpSecretEncoded));
        afterAfter((req, res) -> MDC.clear());

        before(apiRoot + "*", (req, res) -> {
            if (!new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret)) {
                logger.info("Returning 401 because token was not verified");
                halt(401);
            }
            res.header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        });

        //DSM internal routes
        EventUtil eventUtil = new EventUtil();
        NotificationUtil notificationUtil = new NotificationUtil(cfg);

        setupPubSub(cfg, notificationUtil);

        get(apiRoot + RoutePath.BSP_KIT_QUERY_PATH, new BSPKitRoute(notificationUtil), new JsonTransformer());
        get(apiRoot + RoutePath.BSP_KIT_REGISTERED, new BSPKitRegisteredRoute(), new JsonTransformer());
        get(apiRoot + RoutePath.CLINICAL_KIT_ENDPOINT, new ClinicalKitsRoute(notificationUtil), new JsonTransformer());

        if (!cfg.getBoolean("ui.production")) {
            post(apiRoot + RoutePath.SHIP_KIT_ENDPOINT, new JuniperShipKitRoute(), new JsonTransformer());

            StatusKitRoute statusKitRoute = new StatusKitRoute();
            get(apiRoot + RoutePath.KIT_STATUS_ENDPOINT_STUDY, statusKitRoute, new JsonTransformer());
            get(apiRoot + RoutePath.KIT_STATUS_ENDPOINT_JUNIPER_KIT_ID, statusKitRoute, new JsonTransformer());
            get(apiRoot + RoutePath.KIT_STATUS_ENDPOINT_PARTICIPANT_ID, statusKitRoute, new JsonTransformer());
            post(apiRoot + RoutePath.KIT_STATUS_ENDPOINT_KIT_IDS, statusKitRoute, new JsonTransformer());
        }

        if (!cfg.getBoolean("ui.production")) {
            get(apiRoot + RoutePath.CREATE_CLINICAL_KIT_ENDPOINT, new CreateClinicalDummyKitRoute(new OncHistoryDetailDaoImpl()),
                    new JsonTransformer());

            get(apiRoot + RoutePath.CREATE_CLINICAL_KIT_ENDPOINT_WITH_PARTICIPANT, new CreateClinicalDummyKitRoute(
                    new OncHistoryDetailDaoImpl()), new JsonTransformer());
            get(apiRoot + RoutePath.DUMMY_ENDPOINT, new CreateBSPDummyKitRoute(), new JsonTransformer());
        }


        String auth0Signer = cfg.getString(ApplicationConfigConstants.AUTH0_SIGNER);

        SecurityUtil.init(auth0Domain, auth0claimNameSpace, auth0Signer);

        // path is: /app/drugs (this gets the list of display names)
        DrugRoute drugRoute = new DrugRoute();
        get(appRoute + RoutePath.DRUG_LIST_REQUEST, drugRoute, new JsonTransformer());
        get(uiRoot + RoutePath.DRUG_LIST_REQUEST, drugRoute, new JsonTransformer());

        CancerRoute cancerRoute = new CancerRoute();
        get(appRoute + RoutePath.CANCER_LIST_REQUEST, cancerRoute, new JsonTransformer());
        get(uiRoot + RoutePath.CANCER_LIST_REQUEST, cancerRoute, new JsonTransformer());

        UserUtil userUtil = new UserUtil();

        auth0Util = new Auth0Util(cfg.getString(ApplicationConfigConstants.AUTH0_ACCOUNT),
                cfg.getStringList(ApplicationConfigConstants.AUTH0_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.AUTH0_CLIENT_KEY), cfg.getString(ApplicationConfigConstants.AUTH0_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_KEY), cfg.getString(ApplicationConfigConstants.AUTH0_MGT_SECRET),
                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_API_URL), cfg.getString(ApplicationConfigConstants.AUTH0_AUDIENCE));

        before(infoRoot + RoutePath.PARTICIPANT_STATUS_REQUEST, (req, res) -> {
            String tokenFromHeader = Utility.getTokenFromHeader(req);
            Auth0Util.verifyAuth0Token(tokenFromHeader, cfg.getString(ApplicationConfigConstants.AUTH0_DOMAIN), ddpSecret, signer,
                    ddpSecretEncoded);
        });

        get(infoRoot + RoutePath.PARTICIPANT_STATUS_REQUEST, new ParticipantStatusRoute(), new JsonNullTransformer());

        // requests from frontend
        before(uiRoot + "*", (req, res) -> {
            if (!"OPTIONS".equals(req.requestMethod())) {
                if (!req.pathInfo().contains(RoutePath.AUTHENTICATION_REQUEST)) {
                    String tokenFromHeader = Utility.getTokenFromHeader(req);

                    boolean isTokenValid = false;
                    if (StringUtils.isNotBlank(tokenFromHeader)) {
                        isTokenValid = new JWTRouteFilter(auth0Domain).isAccessAllowed(req, true, null);
                    }
                    if (!isTokenValid) {
                        halt(401, SecurityUtil.ResultType.AUTHENTICATION_ERROR.toString());
                    }
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
        post(uiRoot + RoutePath.AUTHENTICATION_REQUEST, authenticationRoute, new JsonTransformer());

        KitUtil kitUtil = new KitUtil();

        PatchUtil patchUtil = new PatchUtil();

        setupExternalShipperLookup(cfg.getString(ApplicationConfigConstants.EXTERNAL_SHIPPER));
        //        GBFRequestUtil gbfRequestUtil = new GBFRequestUtil();

        setupShippingRoutes(notificationUtil, auth0Util, userUtil, cfg.getString(ApplicationConfigConstants.AUTH0_DOMAIN));

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
        patch(uiRoot + RoutePath.USER_SETTINGS_REQUEST, new UserSettingRoute(), new JsonTransformer());

        setupJobs(cfg, kitUtil, notificationUtil, eventUtil);

        //TODO - redo with pubsub
        JavaHeapDumper heapDumper = new JavaHeapDumper();
        get(uiRoot + "/heapDump", new Route() {
            @Override
            public Object handle(Request request, Response response) throws Exception {
                logger.info("Received request to create java heap dump");
                String gcpName = DSMConfig.getSqlFromConfig(ApplicationConfigConstants.GOOGLE_PROJECT_NAME);
                heapDumper.dumpHeapToBucket(gcpName + "_dsm_heapdumps");
                return null;
            }
        }, new JsonTransformer());
        logger.info("Finished setting up DSM custom routes and jobs...");
    }

    private void setupCohortTagRoutes() {
        post(uiRoot + RoutePath.CREATE_COHORT_TAG, new CreateCohortTagRoute(), new JsonTransformer());
        post(uiRoot + RoutePath.BULK_CREATE_COHORT_TAGS, new BulkCreateCohortTagRoute(), new JsonTransformer());
        delete(uiRoot + RoutePath.DELETE_COHORT_TAG, new DeleteCohortTagRoute(), new JsonTransformer());
    }

    private void setupPubSub(@NonNull Config cfg, NotificationUtil notificationUtil) {
        String projectId = cfg.getString(gcpPathToPubsubProjectId);
        String subscriptionId = cfg.getString(gcpPathToPubsubSub);
        String dsmToDssSubscriptionId = cfg.getString(gcpPathToDssToDsmSub);
        String dsmTasksSubscriptionId = cfg.getString(gcpPathToDsmTasksSub);
        String mercuryDsmSubscriptionId = cfg.getString(gcpPathToMercuryToDsmSub);
        String antivirusDsmSubscriptionId = cfg.getString(gcpPathToAntiVirusSub);

        logger.info("Setting up pubsub for {}/{}", projectId, subscriptionId);

        try {
            // Instantiate an asynchronous message receiver.
            MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
                // Handle incoming message, then ack the received message.
                try {
                    TransactionWrapper.inTransaction(conn -> {
                        PubSubLookUp.processCovidTestResults(conn, message, notificationUtil);
                        logger.info("Processing the message finished");
                        consumer.ack();
                        return null;
                    });

                } catch (Exception ex) {
                    logger.info("about to nack the message", ex);
                    consumer.nack();
                    ex.printStackTrace();
                }
            };

            Subscriber subscriber = null;
            ProjectSubscriptionName resultSubName = ProjectSubscriptionName.of(projectId, subscriptionId);
            ExecutorProvider resultsSubExecProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();
            subscriber = Subscriber.newBuilder(resultSubName, receiver).setParallelPullCount(1).setExecutorProvider(resultsSubExecProvider)
                    .setMaxAckExtensionPeriod(org.threeten.bp.Duration.ofSeconds(120)).build();
            try {
                subscriber.startAsync().awaitRunning(1L, TimeUnit.MINUTES);
                logger.info("Started pubsub subscription receiver for {}", subscriptionId);
            } catch (TimeoutException e) {
                throw new RuntimeException("Timed out while starting pubsub subscription " + subscriptionId, e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get results from pubsub ", e);
        }

        logger.info("Setting up pubsub for {}/{}", projectId, dsmToDssSubscriptionId);

        try {
            PubSubResultMessageSubscription.dssToDsmSubscriber(projectId, dsmToDssSubscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            DSMtasksSubscription.subscribeDSMtasks(projectId, dsmTasksSubscriptionId);
        } catch (Exception e) {
            throw new DsmInternalError("Error initializing DSMtasksSubscription", e);
        }

        try {
            MercuryOrderStatusListener.subscribeToOrderStatus(projectId, mercuryDsmSubscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            logger.info("Setting up pupsub for somatic antivirus scanning {}/{}", projectId, antivirusDsmSubscriptionId);
            AntivirusScanningStatusListener.subscribeToAntiVirusStatus(projectId, antivirusDsmSubscriptionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("Pubsub setup complete");
    }

    private void setupShippingRoutes(@NonNull NotificationUtil notificationUtil, @NonNull Auth0Util auth0Util, @NonNull UserUtil userUtil,
                                     @NonNull String auth0Domain) {
        get(uiRoot + RoutePath.KIT_REQUESTS_PATH, new KitRequestRoute(), new JsonTransformer());

        post(uiRoot + RoutePath.FINAL_SCAN_REQUEST, new KitFinalScanRoute(), new JsonTransformer());
        post(uiRoot + RoutePath.RGP_FINAL_SCAN_REQUEST, new RGPKitFinalScanRoute(), new JsonTransformer());
        post(uiRoot + RoutePath.TRACKING_SCAN_REQUEST, new KitTrackingScanRoute(), new JsonTransformer());
        post(uiRoot + RoutePath.SENT_KIT_REQUEST, new SentKitRoute(), new JsonTransformer());
        post(uiRoot + RoutePath.RECEIVED_KIT_REQUEST, new ReceivedKitsRoute(notificationUtil), new JsonTransformer());
        post(uiRoot + RoutePath.INITIAL_SCAN_REQUEST, new KitInitialScanRoute(), new JsonTransformer());

        KitDeactivationRoute kitDeactivationRoute = new KitDeactivationRoute(notificationUtil);
        patch(uiRoot + RoutePath.DEACTIVATE_KIT_REQUEST, kitDeactivationRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.ACTIVATE_KIT_REQUEST, kitDeactivationRoute, new JsonTransformer());

        patch(uiRoot + RoutePath.AUTHORIZE_KIT, new KitAuthorizationRoute(), new JsonTransformer());

        KitExpressRoute kitExpressRoute = new KitExpressRoute(notificationUtil);
        get(uiRoot + RoutePath.EXPRESS_KIT_REQUEST, kitExpressRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.EXPRESS_KIT_REQUEST, kitExpressRoute, new JsonTransformer());

        LabelSettingRoute labelSettingRoute = new LabelSettingRoute();
        get(uiRoot + RoutePath.LABEL_SETTING_REQUEST, labelSettingRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.LABEL_SETTING_REQUEST, labelSettingRoute, new JsonTransformer());

        post(uiRoot + RoutePath.KIT_UPLOAD_REQUEST, new KitUploadRoute(notificationUtil), new JsonTransformer());

        KitLabelRoute kitLabelRoute = new KitLabelRoute();
        get(uiRoot + RoutePath.KIT_LABEL_REQUEST, kitLabelRoute, new JsonTransformer());
        post(uiRoot + RoutePath.KIT_LABEL_REQUEST, kitLabelRoute, new JsonTransformer());

        get(uiRoot + RoutePath.SEARCH_KIT, new KitSearchRoute(), new JsonTransformer());

        KitDiscardRoute kitDiscardRoute = new KitDiscardRoute(auth0Util, auth0Domain);
        get(uiRoot + RoutePath.DISCARD_SAMPLES, kitDiscardRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.DISCARD_SAMPLES, kitDiscardRoute, new JsonTransformer());
        post(uiRoot + RoutePath.DISCARD_UPLOAD, kitDiscardRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.DISCARD_SHOW_UPLOAD, kitDiscardRoute, new JsonTransformer());
        post(uiRoot + RoutePath.DISCARD_CONFIRM, kitDiscardRoute, new JsonTransformer());
    }

    //Routes used by medical record
    private void setupMedicalRecordRoutes(@NonNull Config cfg, @NonNull NotificationUtil notificationUtil, @NonNull PatchUtil patchUtil) {
        //Medical Record
        get(uiRoot + RoutePath.ASSIGNEE_REQUEST, new AssigneeRoute(), new JsonTransformer());

        InstitutionRoute institutionRoute = new InstitutionRoute();
        post(uiRoot + RoutePath.INSTITUTION_REQUEST, institutionRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.INSTITUTION_REQUEST, institutionRoute, new JsonTransformer());

        DownloadPDFRoute pdfRoute = new DownloadPDFRoute();
        post(uiRoot + RoutePath.DOWNLOAD_PDF + DownloadPDFRoute.PDF, pdfRoute, new JsonTransformer());
        post(uiRoot + RoutePath.DOWNLOAD_PDF + DownloadPDFRoute.BUNDLE, pdfRoute, new JsonTransformer());
        get(uiRoot + DownloadPDFRoute.PDF, pdfRoute, new JsonTransformer());

        patch(uiRoot + RoutePath.ASSIGN_PARTICIPANT_REQUEST,
                new AssignParticipantRoute(cfg.getString(ApplicationConfigConstants.GET_DDP_PARTICIPANT_ID),
                        cfg.getString(ApplicationConfigConstants.EMAIL_FRONTEND_URL_FOR_LINKS), notificationUtil), new JsonTransformer());

        ViewFilterRoute viewFilterRoute = new ViewFilterRoute(patchUtil);
        //gets filter names for user for this realm (shared filters and user's filters
        get(uiRoot + RoutePath.GET_FILTERS, viewFilterRoute, new JsonTransformer());
        get(uiRoot + RoutePath.GET_DEFAULT_FILTERS, viewFilterRoute, new JsonTransformer());
        //saves the current Filter Parameters with a name for future use
        patch(uiRoot + RoutePath.SAVE_FILTER, viewFilterRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.FILTER_DEFAULT, viewFilterRoute, new JsonTransformer());

        FilterRoute filterRoute = new FilterRoute();
        //returns List[] that is filtered based on the filterName
        get(uiRoot + RoutePath.APPLY_FILTER, filterRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.FILTER_LIST, filterRoute, new JsonTransformer());
        //gets the participant to go to the tissue that was clicked on
        get(uiRoot + RoutePath.GET_PARTICIPANT, new GetParticipantRoute(), new JsonTransformer());

        MedicalRecordLogRoute medicalRecordLogRoute = new MedicalRecordLogRoute();
        get(uiRoot + RoutePath.MEDICAL_RECORD_LOG_REQUEST, medicalRecordLogRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.MEDICAL_RECORD_LOG_REQUEST, medicalRecordLogRoute, new JsonTransformer());

        //        PermalinkRoute permalinkRoute = new PermalinkRoute();
        //        get(UI_ROOT + RoutePath.PERMALINK_PARTICIPANT_REQUEST, permalinkRoute, new JsonTransformer());
        //        get(UI_ROOT + RoutePath.PERMALINK_INSTITUTION_REQUEST, permalinkRoute, new JsonTransformer());

        get(uiRoot + RoutePath.LOOKUP, new LookupRoute(), new JsonTransformer());

        FieldSettingsRoute fieldSettingsRoute = new FieldSettingsRoute();
        get(uiRoot + RoutePath.FIELD_SETTINGS_ROUTE, fieldSettingsRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.FIELD_SETTINGS_ROUTE, fieldSettingsRoute, new JsonTransformer());

        get(uiRoot + RoutePath.DISPLAY_SETTINGS_ROUTE, new DisplaySettingsRoute(patchUtil), new JsonTransformer());

        post(uiRoot + RoutePath.DOWNLOAD_PARTICIPANT_LIST_ROUTE, new DownloadParticipantListRoute());

        post(uiRoot + RoutePath.ONC_HISTORY_ROUTE, new OncHistoryUploadRoute(), new JsonTransformer());

        get(uiRoot + RoutePath.ONC_HISTORY_TEMPLATE_ROUTE, new OncHistoryTemplateRoute());
    }

    private void setupMRAbstractionRoutes() {
        AbstractionFormControlRoute abstractionFormControlRoute = new AbstractionFormControlRoute();
        get(uiRoot + RoutePath.ABSTRACTION_FORM_CONTROLS, abstractionFormControlRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.ABSTRACTION_FORM_CONTROLS, abstractionFormControlRoute, new JsonTransformer());

        post(uiRoot + RoutePath.ABSTRACTION, new AbstractionRoute(), new JsonTransformer());
    }

    private void setupMiscellaneousRoutes() {
        MailingListRoute mailingListRoute = new MailingListRoute();
        get(uiRoot + RoutePath.MAILING_LIST_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, mailingListRoute,
                new JsonTransformer());

        ParticipantExitRoute participantExitRoute = new ParticipantExitRoute();
        get(uiRoot + RoutePath.PARTICIPANT_EXIT_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, participantExitRoute,
                new JsonTransformer());
        post(uiRoot + RoutePath.PARTICIPANT_EXIT_REQUEST, participantExitRoute, new JsonTransformer());

        TriggerSurveyRoute triggerSurveyRoute = new TriggerSurveyRoute();
        get(uiRoot + RoutePath.TRIGGER_SURVEY + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, triggerSurveyRoute,
                new JsonTransformer());
        post(uiRoot + RoutePath.TRIGGER_SURVEY, triggerSurveyRoute, new JsonTransformer());

        get(uiRoot + RoutePath.EVENT_TYPES + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, new EventTypeRoute(),
                new JsonTransformer());

        ParticipantEventRoute participantEventRoute = new ParticipantEventRoute();
        get(uiRoot + RoutePath.PARTICIPANT_EVENTS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, participantEventRoute,
                new JsonTransformer());
        post(uiRoot + RoutePath.SKIP_PARTICIPANT_EVENTS, participantEventRoute, new JsonTransformer());

        post(uiRoot + RoutePath.NDI_REQUEST, new NDIRoute(), new JsonTransformer());

        DrugListRoute drugListRoute = new DrugListRoute();
        get(uiRoot + RoutePath.FULL_DRUG_LIST_REQUEST, drugListRoute, new JsonTransformer());
        patch(uiRoot + RoutePath.FULL_DRUG_LIST_REQUEST, drugListRoute, new JsonTransformer());

        AddFamilyMemberRoute addFamilyMemberRoute = new AddFamilyMemberRoute();
        post(uiRoot + RoutePath.ADD_FAMILY_MEMBER, addFamilyMemberRoute, new JsonTransformer());

        GetParticipantDataRoute getParticipantDataRoute = new GetParticipantDataRoute();
        get(uiRoot + RoutePath.GET_PARTICIPANT_DATA, getParticipantDataRoute, new JsonTransformer());
    }

    private void setupAdminRoutes() {
        StudyRoleRoute studyRoleRoute = new StudyRoleRoute();
        get(uiRoot + RoutePath.STUDY_ROLE, studyRoleRoute, new JsonTransformer());

        UserRoleRoute userRoleRoute = new UserRoleRoute();
        get(uiRoot + RoutePath.USER_ROLE, userRoleRoute, new JsonTransformer());
        post(uiRoot + RoutePath.USER_ROLE, userRoleRoute, new JsonTransformer());
        put(uiRoot + RoutePath.USER_ROLE, userRoleRoute, new JsonTransformer());

        UserRoute userRoute = new UserRoute();
        post(uiRoot + RoutePath.USER, userRoute, new JsonTransformer());
        put(uiRoot + RoutePath.USER, userRoute, new JsonTransformer());

        RegisterParticipantRoute registerParticipantRoute = new RegisterParticipantRoute();
        post(uiRoot + RoutePath.REGISTER_PARTICIPANT, registerParticipantRoute, new JsonTransformer());
    }


    private void setupSomaticUploadRoutes(@NonNull Config cfg) {
        SomaticResultUploadService somaticResultUploadService = SomaticResultUploadService.fromConfig(cfg);
        post(uiRoot + RoutePath.SOMATIC_DOCUMENT_ROUTE,
                new PostSomaticResultUploadRoute(somaticResultUploadService), new JsonTransformer());
        delete(uiRoot + RoutePath.SOMATIC_DOCUMENT_ROUTE, new DeleteSomaticResultRoute(somaticResultUploadService), new JsonTransformer());
        get(uiRoot + RoutePath.SOMATIC_DOCUMENT_ROUTE,
                new GetSomaticResultsRoute(somaticResultUploadService), new JsonTransformer());
        post(uiRoot + RoutePath.TRIGGER_SOMATIC_SURVEY,
                new TriggerSomaticResultSurveyRoute(somaticResultUploadService), new JsonTransformer());
    }

    private void setupSharedRoutes(@NonNull KitUtil kitUtil, @NonNull NotificationUtil notificationUtil, @NonNull PatchUtil patchUtil) {
        DashboardRoute dashboardRoute = new DashboardRoute(kitUtil);
        get(uiRoot + RoutePath.DASHBOARD_REQUEST, dashboardRoute, new JsonTransformer());
        get(uiRoot + RoutePath.DASHBOARD_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.START + RoutePath.ROUTE_SEPARATOR
                + RequestParameter.END, dashboardRoute, new JsonTransformer());
        get(uiRoot + RoutePath.SAMPLE_REPORT_REQUEST, dashboardRoute, new JsonTransformer());
        get(uiRoot + RoutePath.SAMPLE_REPORT_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.START + RoutePath.ROUTE_SEPARATOR
                + RequestParameter.END, dashboardRoute, new JsonTransformer());

        AllowedRealmsRoute allowedRealmsRoute = new AllowedRealmsRoute();
        get(uiRoot + RoutePath.ALLOWED_REALMS_REQUEST, allowedRealmsRoute, new JsonTransformer());
        get(uiRoot + RoutePath.STUDIES, allowedRealmsRoute, new JsonTransformer());

        KitTypeRoute kitTypeRoute = new KitTypeRoute(kitUtil);
        get(uiRoot + RoutePath.KIT_TYPES_REQUEST + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, kitTypeRoute,
                new JsonTransformer());
        get(uiRoot + RoutePath.UPLOAD_REASONS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, kitTypeRoute, new JsonTransformer());
        get(uiRoot + RoutePath.CARRIERS + RoutePath.ROUTE_SEPARATOR + RequestParameter.REALM, new CarrierServiceRoute(),
                new JsonTransformer());

        patch(uiRoot + RoutePath.PATCH, new PatchRoute(notificationUtil, patchUtil), new JsonTransformer());

        get(uiRoot + RoutePath.DASHBOARD, new NewDashboardRoute(), new JsonTransformer());
    }

    private void setupPubSubPublisherRoutes(Config config) {
        String projectId = config.getString(gcpPathToPubsubProjectId);
        String dsmToDssTopicId = config.getString(gcpPathToDsmToDssTopic);

        EditParticipantPublisherRoute editParticipantPublisherRoute = new EditParticipantPublisherRoute(projectId, dsmToDssTopicId);
        put(uiRoot + RoutePath.EDIT_PARTICIPANT, editParticipantPublisherRoute, new JsonTransformer());

        EditParticipantMessageReceiverRoute editParticipantMessageReceiverRoute = new EditParticipantMessageReceiverRoute();
        get(uiRoot + RoutePath.EDIT_PARTICIPANT_MESSAGE, editParticipantMessageReceiverRoute, new JsonTransformer());

        String mercuryTopicId = config.getString(gcpPathToDsmToMercuryTopic);
        if (!config.getBoolean("ui.production")) {
            PostMercuryOrderDummyRoute postMercuryOrderDummyRoute = new PostMercuryOrderDummyRoute(projectId, mercuryTopicId);
            post(apiRoot + RoutePath.SUBMIT_MERCURY_ORDER, postMercuryOrderDummyRoute, new JsonTransformer());
        }

        FileDownloadService fileDownloadService = FileDownloadService.fromConfig(config);
        get(uiRoot + RoutePath.DOWNLOAD_PARTICIPANT_FILE, new DownloadParticipantFileRoute(fileDownloadService), new JsonTransformer());

        post(uiRoot + RoutePath.SUBMIT_MERCURY_ORDER, new PostMercuryOrderRoute(projectId, mercuryTopicId), new JsonTransformer());

        GetMercuryEligibleSamplesRoute getMercuryEligibleSamplesRoute = new GetMercuryEligibleSamplesRoute(
                new MercurySampleDao(), projectId, mercuryTopicId, new KitDaoImpl());
        get(uiRoot + RoutePath.MERCURY_SAMPLES_ROUTE, getMercuryEligibleSamplesRoute, new JsonTransformer());

        GetMercuryOrdersRoute getMercuryOrdersRoute = new GetMercuryOrdersRoute(
                new MercurySampleDao(), new ClinicalOrderDao(), projectId, mercuryTopicId);
        get(uiRoot + RoutePath.GET_MERCURY_ORDERS_ROUTE, getMercuryOrdersRoute, new JsonTransformer());

    }

    private void setupJobs(@NonNull Config cfg, @NonNull KitUtil kitUtil, @NonNull NotificationUtil notificationUtil,
                           @NonNull EventUtil eventUtil) {
        String schedulerName = null;
        if (cfg.getBoolean(ApplicationConfigConstants.QUARTZ_ENABLE_JOBS)) {
            logger.info("Setting up jobs");
            try {
                Scheduler scheduler = new StdSchedulerFactory().getScheduler();
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

                createScheduleJob(scheduler, eventUtil, notificationUtil, DDPEventJob.class, "TRIGGER_DDP_EVENT",
                        cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_EXPRESSION_FOR_DDP_EVENT_TRIGGER),
                        new DDPEventTriggerListener(), null);

                // currently not needed anymore but might come back
                // createScheduleJob(scheduler, eventUtil, notificationUtil, ExternalShipperJob.class, "CHECK_EXTERNAL_SHIPPER",
                // cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_EXPRESSION_FOR_EXTERNAL_SHIPPER),
                // new ExternalShipperTriggerListener(), cfg);

                createScheduleJob(scheduler, null, null, EasypostShipmentStatusJob.class, "CHECK_STATUS_SHIPMENT",
                        cfg.getString(ApplicationConfigConstants.QUARTZ_CRON_STATUS_SHIPMENT), new EasypostShipmentStatusTriggerListener(),
                        cfg);


                logger.info("Setup Job Scheduler...");
                try {
                    scheduler.start();
                    logger.info("Job Scheduler setup complete.");
                } catch (Exception ex) {
                    throw new RuntimeException("Unable to setup Job Scheduler.", ex);
                }
            } catch (SchedulerException e) {
                throw new RuntimeException("Could not create scheduler ", e);
            }
        }
        setupErrorNotifications(cfg, schedulerName);
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
    }

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
                logger.info("Error notification setup complete. If log4j.xml is configured, notifications will be sent to " + slackChannel
                        + ".");
            } else {
                logger.warn("Skipping error notification setup.");
            }

        }
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

}
