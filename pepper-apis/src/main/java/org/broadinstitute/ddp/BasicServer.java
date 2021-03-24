package org.broadinstitute.ddp;

import com.easypost.EasyPost;
import com.google.gson.Gson;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.NumberGauge;
import com.netflix.servo.publish.*;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.datstat.FollowUpSurveyRecord;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.email.EmailRecord;
import org.broadinstitute.ddp.loggers.ErrorNotificationAppender;
import org.broadinstitute.ddp.loggers.StackdriverErrorAppender;
import org.broadinstitute.ddp.metrics.GoogleMonitoringV3MetricObserver;
import org.broadinstitute.ddp.security.CookieUtil;
import org.broadinstitute.ddp.security.SecurityHelper;
import org.broadinstitute.ddp.util.BasicTriggerListener;
import org.broadinstitute.ddp.util.Utility;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.ResponseTransformer;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static spark.Spark.*;

/**
 * Main class for the spark application. Sets up all the routing, monitoring, jobs, etc.
 */
public class BasicServer
{
    private static final Logger logger = LoggerFactory.getLogger(BasicServer.class);

    public static final String GOOGLE_CRED_ENV_VAR = "GOOGLE_APPLICATION_CREDENTIALS";

    public static final String DDP_ROUTE = "/ddp/";

    public static final String COUNTRIES = "countries";
    public static final String DRUGS = "drugs";
    public static final String IRBCHECK = "irbcheck";
    public static final String UPCHECK = "upcheck";
    public static final String SEND_PARTICIPANT_EMAIL = "sendparticipantemail";

    public enum DDPRouteTypes
    {
        KIT, PARTICIPANT, EXIT, MR, CONSENT_PDF, RELEASE_PDF, MAILING_LIST, PARTICIPANT_EVENT, FOLLOWUP_SURVEY
    }

    public enum ExtraUIRouteTypes
    {
        CONSENT_PDF, RELEASE_PDF, DRUG_LIST
    }

    //monitoring
    protected static final Counter calls = Monitors.newCounter("api_calls");
    protected static final AtomicInteger fiveHundredErrors = new AtomicInteger(0);
    protected static final NumberGauge fiveHundredErrorGauge = new NumberGauge(MonitorConfig.builder("500_error_gauge").build(), fiveHundredErrors);
    protected static final AtomicInteger dbUp = new AtomicInteger(0);
    protected static final NumberGauge dbUpGauge = new NumberGauge(MonitorConfig.builder("db_up_gauge").build(), dbUp);
    private static final AtomicInteger emailHealthy = new AtomicInteger(0);
    private static final NumberGauge emailHealthyGauge = new NumberGauge(MonitorConfig.builder("email_queue_ok_gauge").build(), emailHealthy);
    private static final AtomicInteger followUpHealthy = new AtomicInteger(0);
    private static final NumberGauge followUpHealthyGauge = new NumberGauge(MonitorConfig.builder("followup_queue_ok_gauge").build(), followUpHealthy);

    //explicitly wire up the metrics using a static initializer
    static {
        DefaultMonitorRegistry.getInstance().register(fiveHundredErrorGauge);
        DefaultMonitorRegistry.getInstance().register(calls);
        DefaultMonitorRegistry.getInstance().register(dbUpGauge);
        DefaultMonitorRegistry.getInstance().register(emailHealthyGauge);
        DefaultMonitorRegistry.getInstance().register(followUpHealthyGauge);
    }

    protected void configureServer(@NonNull Config config) {
        logger.info("Property source: " + config.getString("portal.environment"));

        logger.info("Configuring the server...");

        threadPool(-1, -1, 30 * 1000);
        port(config.getInt("portal.port"));

        setupDB(config);
        setupMonitoring(config);
        setupErrorNotifications(config, null);
        setupRouting(config);

        logger.info("Server configuration complete.");
    }

    /**
     * Sets up the TransactionWrapper class.
     *
     * @param config
     */
    protected void setupDB(@NonNull Config config)
    {
        logger.info("Setup the DB...");

        boolean skipSsl = false;

        //see if we should skip SSL
        if ((config.hasPath("portal.dbSkipSsl"))&&(config.getBoolean("portal.dbSkipSsl"))) {
            logger.warn("DB connection will not use SSL.");
            skipSsl = true;
        }

        int maxConnections = config.getInt("portal.maxConnections");
        String dbUrl = config.getString("portal.dbUrl");

        updateDB(dbUrl);

        //setup the mysql transaction/connection utility
        TransactionWrapper.init(maxConnections, dbUrl, config, skipSsl);

        //make sure we can connect to DB
        if (!Utility.dbCheck())
        {
            throw new RuntimeException("DB connection error.");
        }

        logger.info("DB setup complete.");
    }

    /**
     * Setup StackDriver or write to a local file for monitoring.
     */
    protected void setupMonitoring(@NonNull Config config)
    {
        logger.info("Setup monitoring...");

        if (config.hasPath("portal.useGoogleMonitoring")) {

            boolean useGoogleMonitoring = config.getBoolean("portal.useGoogleMonitoring");
            String environmentType = config.getString("portal.environment");

            //add something extra in case we have multiple applications in the same google project
            if (config.hasPath("portal.monitoringPrefix")) {
                environmentType = config.getString("portal.monitoringPrefix") + "_" + environmentType;
            }

            String googleProject = config.getString("portal.googleProjectName");
            int timeIntervalInSeconds = config.getInt("portal.monitoringIntervalInSeconds");

            PollScheduler scheduler = PollScheduler.getInstance();
            scheduler.start();
            MetricObserver fileObserver = new FileMetricObserver(googleProject, new File(File.separator + "tmp"));

            MetricObserver googleObserver = new GoogleMonitoringV3MetricObserver("GoogleObserver", googleProject, environmentType);

            MetricObserver transform = null;

            String googleAuthKeyFile = System.getenv(GOOGLE_CRED_ENV_VAR);

            if (useGoogleMonitoring) {
                if (googleAuthKeyFile == null) {
                    logger.info("Google monitoring is enabled, but no auth key supplied via " + GOOGLE_CRED_ENV_VAR + ".  Google monitoring will therefore only work on cloud deployments.");

                    //setup Stackdriver appender in case it is configured in log4j.xml (it is fine if it is not)
                    StackdriverErrorAppender.configure(googleProject, environmentType);
                } else {
                    logger.info("Will use google cloud monitoring with keys from " + googleAuthKeyFile);
                }
                transform = new CounterToRateMetricTransform(googleObserver, timeIntervalInSeconds * 2, TimeUnit.SECONDS);
            } else {
                logger.info("Using basic file metrics observer instead of google monitoring.");
                transform = new CounterToRateMetricTransform(fileObserver, timeIntervalInSeconds * 2, TimeUnit.SECONDS);
            }

            PollRunnable task = new PollRunnable(new MonitorRegistryMetricPoller(), BasicMetricFilter.MATCH_ALL, transform);
            scheduler.addPoller(task, timeIntervalInSeconds, TimeUnit.SECONDS);
            logger.info("Monitoring setup complete.");
        }
        else {
            logger.warn("Skip monitoring setup.");
        }
    }

    protected void setupJobScheduler(@NonNull Scheduler scheduler)
    {
        logger.info("Setup Job Scheduler...");
        try
        {
            scheduler.start();
            logger.info("Job Scheduler setup complete.");
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Unable to setup Job Scheduler.", ex);
        }
    }

    protected void setupEasyPost(@NonNull String easyPostKey)
    {
        logger.info("Setup EasyPost...");
        EasyPost.apiKey = easyPostKey;
        logger.info("EasyPost setup complete.");
    }

    protected void setupErrorNotifications(@NonNull Config config, String schedulerName) {
        logger.info("Setup error notifications...");

        if ((config.hasPath("errorAlert.key"))&&(config.hasPath("errorAlert.recipientAddress"))) {
            ErrorNotificationAppender.configure(config, schedulerName);
            logger.info("Error notification setup complete. If log4j.xml is configured, notifications will be sent to " +
                    config.getString("errorAlert.recipientAddress") + ".");
        }
        else {
            logger.warn("Skipping error notification setup.");
        }
    }

    // this should executed before registering resources
    protected static void enableCORS(final String origin, final String methods, final String headers) {

        options("/*", (request, response) -> {

            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Request-Method", methods);
            response.header("Access-Control-Allow-Headers", headers);
            response.header("Access-Control-Allow-Credentials","true");
            response.type("application/json");
        });
    }

    protected void setupRouting(@NonNull Config config) {
        logger.info("Setup routing...");

        if (config.hasPath("portal.cors")&&config.getBoolean("portal.cors")) {
            enableCORS("http://localhost:4200", "GET,PUT,POST,OPTIONS,PATCH", "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin,");
        }

        String jwtMonitoringSecret = config.getString("portal.jwtMonitoringSecret");
        String jwtDDPSecret = config.hasPath("portal.jwtDdpSecret") ? config.getString("portal.jwtDdpSecret") : null;

        String appRoute = config.hasPath("portal.appRoute") ? config.getString("portal.appRoute") : DDP_ROUTE;

        before("/*", (req, res) -> {
            calls.increment();
        });

        //authenticate calls
        before(appRoute + "*", (req, res) -> {
            String tokenFromHeader = Utility.getTokenFromHeader(req);

            boolean isTokenValid = false;

            if (StringUtils.isNotBlank(tokenFromHeader)) {
                isTokenValid = SecurityHelper.verifyNonUIToken(req.pathInfo().contains(UPCHECK) ? jwtMonitoringSecret : jwtDDPSecret, tokenFromHeader, req.pathInfo().contains(UPCHECK));
            }

            if (!isTokenValid) {
                halt(401, SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString());
            } else if (req.pathInfo().contains(UPCHECK)) {
                performUpChecks(config);
            }
        });

        setupCustomRouting(config);

        after("/*", (req, res) -> {
            if (res.raw().getStatus() == 500) {
                logger.info("Total number of 500 errors logged since application start = " + fiveHundredErrors.incrementAndGet());
            }
        });

        logger.info("Routing setup complete.");

    }

    protected void setupCustomRouting(@NonNull Config config) {
        throw new RuntimeException("This method must be overriden.");
    }

    protected void updateDB(@NonNull String dbUrl) {
        logger.info("Skipping DB update...");
    }

    protected void performUpChecks(Config config) {
        //let's check on the DB while we're here
        if (Utility.dbCheck()) {
            dbUp.set(1); //means db is ok
        } else {
            dbUp.set(0); //db down
        }

        //let's also check on the email queue health if we are doing email jobs
        if ((config.hasPath("portal.emailJobIntervalInSeconds"))&&(config.getInt("portal.emailJobIntervalInSeconds") > 0)) {
            if (EmailRecord.queueCheck()) {
                emailHealthy.set(1); //all emails look good
            } else {
                emailHealthy.set(0); //at least one partially processed email--this could be temporary
            }
        }

        //let's also check on the followup queue health if we are doing edc jobs
        if ((config.hasPath("portal.edcJobIntervalInSeconds"))&&(config.getInt("portal.edcJobIntervalInSeconds") > 0)) {
            if (FollowUpSurveyRecord.queueCheck()) {
                followUpHealthy.set(1); //all followup surveys look good
            } else {
                followUpHealthy.set(0); //at least one partially processed surveys--this could be temporary
            }
        }

        //monitoring jwt was good so let's immediately send back 200 to monitoring cause we're done
        halt(200);
    }
}
