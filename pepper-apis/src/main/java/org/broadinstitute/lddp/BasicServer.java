package org.broadinstitute.lddp;

import com.easypost.EasyPost;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.broadinstitute.lddp.util.Utility;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    

    protected void configureServer(@NonNull Config config) {
        logger.info("Property source: " + config.getString("portal.environment"));

        logger.info("Configuring the server...");

        threadPool(-1, -1, 30 * 1000);
        port(config.getInt("portal.port"));

        setupDB(config);
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

        int maxConnections = config.getInt("portal.maxConnections");
        String dbUrl = config.getString("portal.dbUrl");

        // updateDB(dbUrl);

        //setup the mysql transaction/connection utility
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.DSM, maxConnections, dbUrl));

        //make sure we can connect to DB
        if (!Utility.dbCheck())
        {
            throw new RuntimeException("DB connection error.");
        }

        logger.info("DB setup complete.");
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
        

        //authenticate calls
        before(appRoute + "*", (req, res) -> {
            String tokenFromHeader = Utility.getTokenFromHeader(req);

            boolean isTokenValid = false;

            if (StringUtils.isNotBlank(tokenFromHeader)) {
                isTokenValid = SecurityHelper.verifyNonUIToken(req.pathInfo().contains(UPCHECK) ? jwtMonitoringSecret : jwtDDPSecret, tokenFromHeader, req.pathInfo().contains(UPCHECK));
            }

            if (!isTokenValid) {
                halt(401, SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString());
            }
        });

        setupCustomRouting(config);
        

        logger.info("Routing setup complete.");

    }

    protected void setupCustomRouting(@NonNull Config config) {
        throw new RuntimeException("This method must be overriden.");
    }

    protected void updateDB(@NonNull String dbUrl) {
        logger.info("Skipping DB update...");
    }
    
}
