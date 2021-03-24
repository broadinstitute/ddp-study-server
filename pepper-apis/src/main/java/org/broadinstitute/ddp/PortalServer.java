package org.broadinstitute.ddp;

import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.datstat.*;
import org.broadinstitute.ddp.email.EmailQueueJob;
import org.broadinstitute.ddp.handlers.*;
import org.broadinstitute.ddp.handlers.util.HandlerUtil;
import org.broadinstitute.ddp.routes.GenerateTokenRoute;
import org.broadinstitute.ddp.routes.AccessTokenRoute;
import org.broadinstitute.ddp.security.Auth0Util;
import org.broadinstitute.ddp.security.CookieUtil;
import org.broadinstitute.ddp.security.SecurityHelper;
import org.broadinstitute.ddp.util.*;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.List;

import static spark.Spark.*;
import static spark.Spark.before;

/**
 * Main class for the spark application. Sets up all the routing, monitoring, jobs, etc.
 */
public class PortalServer extends BasicServer
{
    private static final Logger logger = LoggerFactory.getLogger(PortalServer.class);

    public enum StandardizedSurveyPaths
    {
        consent, release
    }

    private String uiRoute;
    private String csrfCookieName;

    /**
     * Configures everything.
     *
     * @param config application settings
     */
    protected void configureServer(@NonNull Config config, MedicalInfoCollector medicalInfoCollector)
    {
        logger.info("Property source: " + config.getString("portal.environment"));
        logger.info("Configuring the server...");

        uiRoute = config.getString("portal.uiRoute");
        csrfCookieName = config.getString("portal.csrfCookieName");

        threadPool(-1,-1,30 * 1000);
        port(config.getInt("portal.port"));

        //use the property below to serve static web pages from another directory on a dev machine (directory for app frontend)
        if (config.hasPath("portal.externalStaticFileLocation"))
        {
            logger.info("Configuring static file location.");
            externalStaticFileLocation(config.getString("portal.externalStaticFileLocation"));
        }
        else
        {
            logger.info("No static file location detected.");
        }

        //check if we should skip Auth0 stuff, by default Auth0 security is always ON!!!
        if (Auth0Util.useAuth0(config)) {
            logger.info("Auth0 security feature is ON.");
        }
        else {
            logger.warn("Auth0 security feature has been turned OFF.");
        }

        boolean splashPageOnly = false;

        if (config.hasPath("portal.splashPageUI")) {
            splashPageOnly = config.getBoolean("portal.splashPageUI");

            if (splashPageOnly) {
                logger.warn("Splash page UI only! Only mailing list functionality will be configured.");
            }
        }

        setupDB(config);

        setupMonitoring(config);

        Scheduler scheduler = null;
        int totalJobs = 0;
        try
        {
            scheduler = new StdSchedulerFactory().getScheduler();
            setupErrorNotifications(config, scheduler.getSchedulerName());
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Unable to create job scheduler and/or setup error notifications.", ex);
        }

        if (config.getInt("portal.emailJobIntervalInSeconds") > 0) { // if <= 0 we will skip running the job
            EmailQueueJob.createScheduledJob(scheduler, config, splashPageOnly);
            totalJobs++;
        }

        EDCClient edc = null;
        if (!splashPageOnly) {
            setupEasyPost(config.getString("portal.easyPostKey"));

            boolean runEdcJob = ((config.getInt("portal.edcJobIntervalInSeconds")) > 0); // if <= 0 we will skip running the job
            if (runEdcJob) {
                totalJobs++;
            }

            edc = setupEDCSystem(config, (runEdcJob) ? scheduler : null);
        }

        totalJobs += setupCustomJobs(scheduler, config, splashPageOnly);

        if (totalJobs > 0) setupJobScheduler(scheduler);

        setupRouting(config, edc, medicalInfoCollector, splashPageOnly);

        logger.info("Server configuration complete.");
    }

    protected int setupCustomJobs(Scheduler scheduler, Config config, boolean splashPageOnly) {
        logger.info("Skip custom job setup.");
        return 0;
    }

    /**
     * Sets up the client class used for EDC system integration.
     * @param config
     * @param scheduler
     * @return
     * @
     */
    private EDCClient setupEDCSystem(@NonNull Config config, Scheduler scheduler)
    {
        logger.info("Setup EDC System...");
        logger.info("Configure EDC System class " + config.getString("portal.edcClassName"));

        EDCClient edc = null;
        try
        {
            edc = (EDCClient)Class.forName(config.getString("portal.edcClassName")).newInstance();
            edc.startup(config, scheduler);

            logger.info("EDC System setup complete.");
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Unable to setup EDC System.", ex);
        }

        return edc;
    }

    /**
     * Sets up request handlers/routes.
     *
     * @param config
     */
    private void setupRouting(@NonNull Config config, EDCClient edc,
                              MedicalInfoCollector medicalInfoCollector, boolean splashPageOnly)
    {
        logger.info("Setup routing...");
        enableCORS("http://localhost:4200", "GET,PUT,POST,OPTIONS,PATCH", "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin,");

        String jwtMonitoringSecret = config.getString("portal.jwtMonitoringSecret");
        String jwtDDPSecret = config.getString("portal.jwtDdpSecret");

        before("/*", (req, res) -> {
            calls.increment();
        });

        //authenticate requests from frontend or a monitoring system
        before(uiRoute + "*", (req, res) -> {
            if (!"OPTIONS".equals(req.requestMethod())) {
                if (!req.pathInfo().contains("auth") && !req.pathInfo().contains(IRBCHECK)) {
                    //always check for token when non-splashPage application and when doing UPCHECK for monitoring
                    if ((!splashPageOnly)||(req.pathInfo().contains(UPCHECK))) {
                        String tokenFromHeader = Utility.getTokenFromHeader(req);
                        boolean isTokenValid = false;
                        if (StringUtils.isNotBlank(tokenFromHeader)) {
                            if (!req.pathInfo().contains(UPCHECK)) {
                                isTokenValid = new CookieUtil().isCookieValid(req.cookie(csrfCookieName), config.getString("portal.jwtSalt").getBytes(), tokenFromHeader, config.getString("portal.jwtSecret"));
                            } else {
                                isTokenValid = SecurityHelper.verifyNonUIToken(jwtMonitoringSecret, tokenFromHeader, true);
                            }
                        }
                        if (!isTokenValid) {
                            halt(401, SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString());
                        } else if (req.pathInfo().contains(UPCHECK)) {
                            performUpChecks(config);
                        }
                    }
                }
            }
        });

        //authenticate calls from things like DSM
        before(DDP_ROUTE + "*", (req, res) -> {
            String tokenFromHeader = Utility.getTokenFromHeader(req);
            boolean isTokenValid = false;
            if (StringUtils.isNotBlank(tokenFromHeader)) {
                isTokenValid = SecurityHelper.verifyNonUIToken(jwtDDPSecret, tokenFromHeader, false);
            }
            if (!isTokenValid) {
                halt(401, SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString());
            }
        });

        //handles mailing list requests and DatStat participation
        post(uiRoute + "person", (!splashPageOnly) ? new PersonRequestHandler(edc, config) :  new PersonRequestHandler(config));


        List<String> ddpRouteTypes = config.getStringList("portal.ddpRouteTypes");
        List<String> portalExtraRouteTypes = config.hasPath("portal.portalExtraRouteTypes") ? config.getStringList("portal.portalExtraRouteTypes") : null;

        //mailing list endpoint for DSM, etc.
        setupDDPNonStudyRoutes(ddpRouteTypes, config);

        if (!splashPageOnly) {

            if ((portalExtraRouteTypes != null) && (portalExtraRouteTypes.stream().anyMatch(str -> str.equals(ExtraUIRouteTypes.CONSENT_PDF.toString())))) {
                get(uiRoute + "participants/:id/" + GetParticipantSmallPdfHandler.PdfType.consentpdf,
                        new GetParticipantSmallPdfHandler(edc, config, StandardizedSurveyPaths.consent.toString(), true ));
            }

            // short term route for a site-wide password for IRB
            post(uiRoute + IRBCHECK, new Route() {
                @Override
                public Object handle(Request request, Response response) throws Exception {
                    boolean goodPassword = config.getString("portal.frontendAccessPwd").equals(request.body());
                    return new IrbAuthResult(goodPassword);
                }
            }, new JsonTransformer());

            //for now we aren't using the admin routes, so we will only expose them for testing
            if (config.getString("portal.environment").equals(Utility.Deployment.UNIT_TEST.toString()))
            {
                setupAdminRouting(config);
            }

            //handles sending emails to participants
            post(uiRoute + SEND_PARTICIPANT_EMAIL, new SendParticipantEmailRequestHandler(edc, config));

            // returns a country code list with 2 letter code and a full name text
            get(uiRoute + COUNTRIES, new GetCountryCodeHandler(edc, config));

            // returns a drug list
            if ((portalExtraRouteTypes != null) && (portalExtraRouteTypes.stream().anyMatch(str -> str.equals(ExtraUIRouteTypes.DRUG_LIST.toString())))) {
                get(uiRoute + DRUGS, new GetDrugListHandler(edc, config));
            }

            if (edc instanceof DatStatUtil) {
                setupSurveyRoute(edc, config);
            }

            post(uiRoute + "auth", new AccessTokenRoute(edc, config), new JsonTransformer());

            setupDDPStudyRoutes(ddpRouteTypes, config, edc, medicalInfoCollector);
        }

        setupPortalCustomRouting(config, edc, medicalInfoCollector, splashPageOnly);

        after("/*", (req, res) -> {
            if (res.raw().getStatus() == 500) {
                logger.info("Total number of 500 errors logged since application start = " + fiveHundredErrors.incrementAndGet());
            }
        });

        logger.info("Routing setup complete.");
    }

    protected void setupPortalCustomRouting(@NonNull Config config, EDCClient edc,
                                 MedicalInfoCollector medicalInfoCollector, boolean splashPageOnly) {
        logger.info("Skip custom routing setup.");
    }

    //Routes used by DSM for non-study stuff (could be used by just splash page app or other small implementations)
    private void setupDDPNonStudyRoutes(@NonNull List<String> ddpRouteTypes, @NonNull Config config) {
        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.MAILING_LIST.toString()))) {
            get(DDP_ROUTE + "mailinglist", new GetMailingListHandler(config));
        }
    }

    //Routes used by DSM for study stuff (participant/survey data)
    private void setupDDPStudyRoutes(@NonNull List<String> ddpRouteTypes, @NonNull Config config, @NonNull EDCClient edc, MedicalInfoCollector medicalInfoCollector)
    {
        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.KIT.toString()))) {
            //sends back all records of {participantId, UUID(maps to requestId, kitType}
            get(DDP_ROUTE + "kitrequests", new GetKitRequestHandler(edc, config));

            //sends back all records of {participantId, UUID(maps to requestId, kitType}
            get(DDP_ROUTE + "kitrequests/:id", new GetKitRequestHandler(edc, config));
        }

        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.PARTICIPANT.toString()))) {
            //send back just participant name for now
            get(DDP_ROUTE + "participants", new GetParticipantRequestHandler(edc, config));

            // returns a participant with id (altPid) added to url
            get(DDP_ROUTE + "participants/:id", new GetParticipantRequestHandler(edc, config));
        }

        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.MR.toString()))) {
            //medical info for a participant
            get(DDP_ROUTE + "participants/:id/medical", new GetParticipantMedicalInfoHandler(edc, config, medicalInfoCollector));

            //institution info for all participants
            get(DDP_ROUTE + "participantinstitutions", new GetParticipantInstitutionInfoHandler(edc, config));

            //all institution requests
            get(DDP_ROUTE + "institutionrequests", new GetInstitutionRequestHandler(edc, config));

            //institution requests after a particular id (submission id internally)
            get(DDP_ROUTE + "institutionrequests/:id", new GetInstitutionRequestHandler(edc, config));
        }

        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.CONSENT_PDF.toString()))) {
            //consent pdf for a participant
            get(DDP_ROUTE + "participants/:id/" + GetParticipantSmallPdfHandler.PdfType.consentpdf,
                    new GetParticipantSmallPdfHandler(edc, config, StandardizedSurveyPaths.consent.toString(), false));
        }

        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.RELEASE_PDF.toString()))) {
            //release pdf for a participant
            get(DDP_ROUTE + "participants/:id/" + GetParticipantSmallPdfHandler.PdfType.releasepdf,
                    new GetParticipantSmallPdfHandler(edc, config, StandardizedSurveyPaths.release.toString(), false));
        }

        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.EXIT.toString()))) {
            //exits participant from study
            post(DDP_ROUTE + "exitparticipantrequest/:id", new ExitParticipantHandler(edc, config));
        }

        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.PARTICIPANT_EVENT.toString()))) {
            //handles participant events sent from DSM
            post(DDP_ROUTE + "participantevent/:id", new ParticipantEventHandler(edc, config));
        }

        if (ddpRouteTypes.stream().anyMatch(str -> str.equals(DDPRouteTypes.FOLLOWUP_SURVEY.toString()))) {
            //returns list of follow-up surveys to DSM
            get(DDP_ROUTE + "followupsurveys", new GetFollowUpListHandler(edc, config));

            //creates participant follow-up surveys
            post(DDP_ROUTE + "followupsurvey/" + HandlerUtil.PATHPARAM_SURVEY, new FollowUpSurveyHandler(edc, config));

            //returns participant follow-up survey status info for a particular survey
            get(DDP_ROUTE + "followupsurvey/" + HandlerUtil.PATHPARAM_SURVEY, new GetParticipantFollowUpInfoHandler(edc, config));
        }
    }

    private void setupAdminRouting(@NonNull Config config)
    {
        String secret = config.getString("portal.jwtSecret");

        //check for valid admin token whenever someone requests this path...
        before("/admin/*", (req, res) -> {
            String jwtToken = Utility.getTokenFromHeader(req);

            if (jwtToken.isEmpty())
            {
                logger.info("BASIC ADMIN ACCESS REQUEST - missing token: " + req.pathInfo());
                halt(401, SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString());
            }

            //NOTE: halts throw exceptions so the code below isn't as pretty as it could be
            boolean authorized = false;

            try
            {
                //check token validity here (will throw exception if invalid)
                //and make sure the user has at least the most basic admin role before proceeding
                authorized = (SecurityHelper.hasRole(secret, jwtToken, SecurityHelper.BASIC_ADMIN_ROLE));
            }
            catch (Exception ex)
            {
                //there's something bad about the token (expired, wrong issuer, etc.)
                logger.info("BASIC ADMIN ACCESS REQUEST - invalid token: ", ex);
                halt(401, SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString());
            }

            //there is a valid token, but it isn't for admins...
            if (!authorized)
            {
                logger.info("BASIC ADMIN ACCESS REQUEST - unauthorized");
                halt(403, SecurityHelper.ResultType.AUTHORIZATION_ERROR.toString());
            }

            logger.info("BASIC ADMIN ACCESS REQUEST - " + SecurityHelper.ResultType.AUTHORIZED.toString().toLowerCase() + ": " + req.pathInfo());

        });

        //generates the admin tokens needed
        post("/adminLogin", new GenerateTokenRoute(SecurityHelper.BASIC_ADMIN_ROLE,
                (config.getInt("portal.tokenDurationInMinutes") * 60),
                secret,
                config.getString("portal.googleAuthClientKey")));

        get("/admin/authorization", (req, res) -> {
            //before has already checked basic authorization for us so we are good if we get this far...
            res.status(200);
            return SecurityHelper.ResultType.AUTHORIZED.toString();
        });
    }

    private void setupSurveyRoute(@NonNull EDCClient edc, @NonNull Config config){
        // build json object that has all the info for the given survey session
        get(uiRoute + HandlerUtil.PATHPARAM_SURVEY + "/surveysession/" + HandlerUtil.PATHPARAM_SESSIONID, new GetSurveySessionHandler(edc, config));

        SurveySessionHandler sessionHandler = new SurveySessionHandler(edc, config);

        // return a new survey session
        post(uiRoute + HandlerUtil.PATHPARAM_SURVEY + "/surveysession", sessionHandler);

        // update a survey session by applying one or more field changes to it
        patch(uiRoute + HandlerUtil.PATHPARAM_SURVEY + "/surveysession/" + HandlerUtil.PATHPARAM_SESSIONID, sessionHandler);

        // final "submit" of a survey.  iterates through list of change request
        // key value pairs, applies changes, and then sets survey status to complete.
        put(uiRoute + HandlerUtil.PATHPARAM_SURVEY + "/surveysession/" + HandlerUtil.PATHPARAM_SESSIONID, sessionHandler);

        get(uiRoute + "surveyworkflowstatus", new GetSurveyWorkflowStatusHandler(edc, config));

        get(uiRoute + "participantsurvey/" + HandlerUtil.PATHPARAM_SURVEY, new GetParticipantSurveySessionIdHandler(edc, config));
    }
}
