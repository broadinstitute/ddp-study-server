package org.broadinstitute.lddp.routes;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.datstat.SurveyConfig;
import org.broadinstitute.lddp.datstat.SurveyInstance;
import org.broadinstitute.lddp.datstat.SurveyService;
import org.broadinstitute.lddp.exception.RecaptchaException;
import org.broadinstitute.lddp.exception.TokenGenerationException;
import org.broadinstitute.lddp.security.Auth0Util;
import org.broadinstitute.lddp.security.AuthPayload;
import org.broadinstitute.lddp.security.AuthResponse;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.broadinstitute.lddp.util.EDCClient;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Generates tokens for UI routes.
 */
public class AccessTokenRoute implements Route
{
    private static final Logger logger = LoggerFactory.getLogger(AccessTokenRoute.class);

    private Config config;
    private EDCClient edc;
    private int defaultDurationInSeconds;
    private SurveyService surveyService = new SurveyService();
    private String csrfCookieName;
    private String secret;
    private String salt;
    private Auth0Util auth0Util = null;

    public AccessTokenRoute(@NonNull EDCClient edc, @NonNull Config config) {
        this.config = config;
        this.edc = edc;
        this.defaultDurationInSeconds = ((config.getInt("portal.accessTokenDurationInDays")) * 60 * 60 * 24);
        this.csrfCookieName = config.getString("portal.csrfCookieName");
        this.secret = config.getString("portal.jwtSecret");
        this.salt = config.getString("portal.jwtSalt");
        this.auth0Util = Auth0Util.configureAuth0Util(config);
    }

    @Override
    public Object handle(@NonNull Request request, @NonNull Response response) {
        response.status(401);
        DatStatUtil dataStatUtil = (DatStatUtil)edc;
        String responseBody = SecurityHelper.ResultType.AUTHENTICATION_ERROR.toString();

        try {
            AuthPayload authPayload = new Gson().fromJson(request.body(), AuthPayload.class);

            String jwtToken = null;

            //if we are using accounts we will have an Auth0 token to verify with Auth0
            if (StringUtils.isNotBlank(authPayload.getAuth0IdToken())) {
                jwtToken =  SecurityHelper.createAccessTokenAndCookieFromAuth0Tokens(response, secret, salt, csrfCookieName,
                        dataStatUtil, auth0Util, authPayload.getAuth0IdToken(), false);
            }
            else {
                //set values from payload by default (create non-Auth0 account-based token)
                //these values are really only read from the token when creating anonymous surveys right now
                String email = authPayload.getEmail();
                String firstName = authPayload.getFirstName();
                String lastName = authPayload.getLastName();

                String participantId = null;

                //right now assume not doing recaptcha and Auth0 at the same time
                boolean recaptchaOk = checkRecaptcha(authPayload);

                //token will be used to access survey route
                if (authPayload.isValidForSurveyBasedAuth()) {
                    SurveyConfig surveyConfig = dataStatUtil.getSurveyConfigMap().get(authPayload.getSurveyName());

                    if (surveyConfig == null) {
                        throw new TokenGenerationException("Unable to retrieve survey config (" + authPayload.getSurveyName() + ").");
                    }

                    //"authenticated" DatStat survey
                    if (!surveyConfig.isAnonymousSurvey()) {
                        SurveyInstance surveyInstance = surveyService.fetchSurveyInstance(dataStatUtil, surveyConfig.getSurveyClass(), authPayload.getSurveySessionId());

                        participantId = surveyInstance.getAltPid();

                        if (StringUtils.isNotBlank(participantId)) {
                            if (dataStatUtil.getParticipantIdByAltPid(participantId) == null) {
                                throw new TokenGenerationException("Unable to find participant " + participantId + " for survey (" + authPayload.getSurveyName() + ", " + authPayload.getSurveySessionId() + ").");
                            }
                        } else {
                            throw new TokenGenerationException("Altpid missing for authenticated survey (" + authPayload.getSurveyName() + ", " + authPayload.getSurveySessionId() + ").");
                        }
                    }
                }

                jwtToken = SecurityHelper.createAccessTokenAndCookie(response, secret, salt, csrfCookieName, Utility.getCurrentEpoch() + defaultDurationInSeconds,
                        firstName, lastName, participantId, email, recaptchaOk, false);
            }

            response.status(200);

            return new AuthResponse(jwtToken);
        }
        catch (Exception ex) {
            logger.error("TOKEN GENERATION - " + responseBody.toLowerCase() + ": ", ex);
        }

        return responseBody;
    }

    private boolean checkRecaptcha(AuthPayload authPayload) {
        boolean recaptchaOk = false;
        String recaptcha = authPayload.getRecaptcha();
        try {
            // if there's a recaptcha, check it.
            if (StringUtils.isNotBlank(recaptcha)) {
                Utility.verifyRecaptcha(recaptcha,
                        config.getString("portal.googleReCaptchaSecret"), config.getString("portal.googleReCaptchaUrl"),
                        config.hasPath("portal.bypassReCaptchaCheck") ? config.getString("portal.bypassReCaptchaCheck") : null,
                        config.getString("portal.environment"));
                recaptchaOk = true;
            }
        }
        catch (RecaptchaException e) {
            logger.error("TOKEN GENERATION - Recaptcha failed for " + new Gson().toJson(authPayload), e);
        }
        return recaptchaOk;
    }
}

