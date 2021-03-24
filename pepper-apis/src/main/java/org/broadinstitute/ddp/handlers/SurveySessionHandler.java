package org.broadinstitute.ddp.handlers;

import com.auth0.jwt.interfaces.Claim;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.org.apache.regexp.internal.RE;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.datstat.*;
import org.broadinstitute.ddp.email.EmailRecord;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.handlers.util.HandlerUtil;
import org.broadinstitute.ddp.handlers.util.Payload;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.security.SecurityHelper;
import org.broadinstitute.ddp.util.EDCClient;
import org.broadinstitute.ddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SurveySessionHandler extends AbstractRequestHandler<Payload> {

    private static final Logger logger = LoggerFactory.getLogger(SurveySessionHandler.class);

    private static final String LOG_PREFIX = "PROCESS SURVEY REQUEST - ";

    public static final String PAYLOAD_PARTICIPANT = "participantId";

    public static final String JSON_SESSIONID = "sessionId";

    public static final String JSON_SURVEY = "survey";

    public SurveySessionHandler(EDCClient edc, Config config) {
        super(Payload.class, edc, config);
    }

    @Override
    protected Result processRequest(@NonNull Payload payload, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    @NonNull String requestMethod, String token, Response response) {
        logger.info(LOG_PREFIX + "Start processing " + requestMethod + " for survey.");

        try {
            String surveyName = pathParams.get(HandlerUtil.PATHPARAM_SURVEY);

            if (StringUtils.isNotBlank(surveyName)) {
                logger.info("Processing " + requestMethod + " request for survey " + surveyName);

                SurveyConfig surveyConfig = ((DatStatUtil) edc).getSurveyConfigMap().get(surveyName);
                if (surveyConfig != null) {
                    Recipient recipient = null;
                    SurveyService service = new SurveyService();

                    if (requestMethod.equals(Utility.RequestMethod.POST.toString())) {
                        if (!surveyConfig.isAllowsPost()) {
                            throw new RuntimeException("The survey " + surveyName + " does not allow posts.");
                        }

                        logger.info(LOG_PREFIX + "Creating survey session...");
                        //first configure the recipient for anonymous surveys
                        if (surveyConfig.isAnonymousSurvey()) {
                            try {
                                Map<String, Claim> claims = SecurityHelper.verifyAndGetClaims(config.getString("portal.jwtSecret"), token);
                                recipient = new Recipient(claims.get(SecurityHelper.CLAIM_FIRSTNAME).asString(),
                                        claims.get(SecurityHelper.CLAIM_LASTNAME).asString(), claims.get(SecurityHelper.CLAIM_EMAIL).asString());
                                recipient.setCurrentStatus(surveyName);
                            }
                            catch (Exception ex) {
                                throw new RuntimeException("An error occurred trying to create the recipient for an anonymous survey. The token may be invalid.", ex);
                            }
                        }

                        String participantAltPid = (String)payload.get(PAYLOAD_PARTICIPANT);
                        if (surveyConfig.isAnonymousSurvey()) {
                            logger.info(LOG_PREFIX + "Survey is an anonymous.");
                            //check if participantAltPid is empty
                            if (participantAltPid == null) {
                                SurveyInstance surveyInstance = service.createSurveyInstance((DatStatUtil) edc, surveyConfig.getSurveyDefinition(), surveyConfig.getSurveyClass(), recipient, payload);
                                String sessionId = surveyInstance.getSurveySession().getSessionId();
                                if (StringUtils.isNotBlank(sessionId)) {
                                    return new Result(200, new Gson().toJson(Collections.singletonMap(JSON_SESSIONID, sessionId)));
                                } else {
                                    throw new RuntimeException("Survey session id for new survey is blank.");
                                }
                            } else {
                                throw new RuntimeException("Altpid included in payload for anonymous survey " + surveyName + ", which requires no altpid.");
                            }
                        } else {
                            logger.info("Survey is NOT anonymous.");
                            //check if participantAltPid is not empty
                            if (participantAltPid != null) {
                                //let's find out more about the survey for security
                                SurveyInstance surveyInstance = (SurveyInstance)surveyConfig.getSurveyClass().newInstance();
                                //make sure we have a good token for this particular survey before posting
                                if (!SecurityHelper.hasValidSurveyAuthorization(config.getString("portal.jwtSecret"), token, surveyConfig.isAnonymousSurvey(),
                                        participantAltPid, (surveyInstance instanceof RecaptchaSecurity),
                                        (surveyInstance.isAccountNeeded()&&(!(surveyInstance instanceof SkipAccountForPost))))) {
                                    return new Result(403, SecurityHelper.ResultType.AUTHORIZATION_ERROR.toString());
                                }

                                // check if participant exists
                                recipient = ((DatStatUtil)edc).getSimpleParticipantInfoByAltPid(participantAltPid);
                                if (recipient != null) {
                                    logger.info(LOG_PREFIX + "Participant with altpid " + participantAltPid + " exists in DatStat.");
                                    String sessionId = createSurveySession(participantAltPid, surveyConfig, service, recipient, payload);
                                    if (sessionId != null) {
                                        return new Result(200, new Gson().toJson(Collections.singletonMap(JSON_SESSIONID, sessionId)));
                                    } else {
                                        throw new RuntimeException("Survey session id for new survey is blank.");
                                    }
                                } else {
                                    throw new RuntimeException("Recipient is null.");
                                }
                            } else {
                                throw new RuntimeException("Altpid missing in payload for survey " + surveyName + ".");
                            }
                        }

                    } else { // PUT AND PATCH
                        logger.info(LOG_PREFIX + "Updating survey session...");

                        if (payload.size() == 0) {
                            throw new RuntimeException("Payload for PUT/PATCH was empty.");
                        }
                        else {
                            boolean patch = requestMethod.equals(Utility.RequestMethod.PATCH.toString());
                            String sessionId = pathParams.get(HandlerUtil.PATHPARAM_SESSIONID);
                            SurveyInstance surveyInstance = service.fetchSurveyInstance((DatStatUtil) edc,
                                    surveyConfig.getSurveyClass(), sessionId);

                            //make sure we have a good token for this particular survey before proceeding
                            if (!SecurityHelper.hasValidSurveyAuthorization(config.getString("portal.jwtSecret"), token, surveyConfig.isAnonymousSurvey(),
                                    surveyInstance.getAltPid(), (surveyInstance instanceof RecaptchaSecurity), surveyInstance.isAccountNeeded())) {
                                return new Result(403, SecurityHelper.ResultType.AUTHORIZATION_ERROR.toString());
                            }

                            String participantId = null;

                            //load participant info into recipient
                            if (!surveyConfig.isAnonymousSurvey()) {
                                participantId = ((DatStatUtil)edc).getParticipantIdByAltPid(surveyInstance.getAltPid());
                                recipient = ((DatStatUtil)edc).getSimpleParticipantInfoById(participantId);

                                //if the participant JUST exited, terminate survey and return result immediately
                                if (terminatedNow(recipient, surveyConfig, service, sessionId)) {
                                    return terminatedResult(patch, sessionId, surveyName);
                                }
                            } else {
                                //for anonymous surveys let's get the recipient info from the survey itself since it may not be in a cookie anymore
                                recipient = surveyInstance.getRecipient();
                                recipient.setCurrentStatus(surveyName);
                            }

                            boolean alreadyCompleted = surveyInstance.getSubmissionStatus().equals(SurveyInstance.SubmissionStatus.COMPLETE);

                            //if this is a PUT only we can generate any missing UUIDs we want for JSON fields that store arrays of objects
                            if (requestMethod.equals(Utility.RequestMethod.PUT.toString())) {
                                surveyInstance.setJsonUUIDNeeded(true);
                            }

                            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                                surveyInstance.applyChange(entry.getKey(), entry.getValue());
                            }

                            if ((requestMethod.equals(Utility.RequestMethod.PUT.toString())) && !alreadyCompleted) {
                                //set the initial completion field -- only gets set once
                                surveyInstance.setSurveyFirstCompleted();

                                surveyInstance.setSubmissionStatus(SurveyInstance.SubmissionStatus.COMPLETE);
                            }

                            //update the lastupdated field -- gets set every time we update the survey
                            surveyInstance.setSurveyLastUpdated();

                            //save the survey changes
                            service.saveSurveyInstance((DatStatUtil) edc, surveyInstance);

                            //if the participant JUST exited, terminate survey and return result immediately
                            if (terminatedNow(participantId, surveyConfig, service, sessionId)) {
                                return terminatedResult(patch, sessionId, surveyName);
                            }

                            //we are done if this is a PATCH
                            if (patch) {
                                return new Result(200, "{\"status\":\"ok\"}");
                            }

                            //set default return values for PUT with values for done
                            HashMap<String, String> returnMap = new HashMap<>();
                            returnMap.put(JSON_SESSIONID, null);
                            returnMap.put(JSON_SURVEY, DatStatUtil.STATUS_DONE);

                            String nextSessionId = null;

                            //current survey for the participant or any anonymous survey
                            if (recipient.getCurrentStatus().equals(surveyName)) {
                                String nextSurvey = ((DatStatUtil) edc).getPortalNextSurvey(surveyName);

                                //let's update the recipient's status so we can use it later
                                recipient.setCurrentStatus(nextSurvey);

                                //these values will be store in the email queue in case we need to recreate this survey object for pdf generation
                                recipient.setCompletedSurveySessionId(sessionId);
                                recipient.setCompletedSurvey(surveyName);

                                //create the next survey instance if we need to
                                if ((!surveyConfig.isAnonymousSurvey()) && (!nextSurvey.equals(DatStatUtil.STATUS_DONE))) {
                                    logger.info("Create next survey " + nextSurvey);
                                    SurveyConfig nextConfig = ((DatStatUtil) edc).getSurveyConfigMap().get(nextSurvey);

                                    nextSessionId = createSurveySession(surveyInstance.getAltPid(), nextConfig, service, recipient, null);

                                    //if the participant JUST exited, terminate survey and return result immediately
                                    if (terminatedNow(participantId, nextConfig, service, nextSessionId)) {
                                        //new survey was created before the participant was exited so we must update the participant's status
                                        surveyInstance.updateParticipantInfo(recipient, (DatStatUtil)edc);
                                        return terminatedResult(patch, nextSessionId, recipient.getCurrentStatus());
                                    }

                                    returnMap.put(JSON_SESSIONID, nextSessionId);
                                    returnMap.put(JSON_SURVEY, recipient.getCurrentStatus());
                                }
                            } else if (!recipient.getCurrentStatus().equals(DatStatUtil.STATUS_DONE)) {
                                String currentSessionId = ((DatStatUtil) edc).getSingleSurveySessionViaUri(((DatStatUtil) edc).getSurveyConfigMap().get(recipient.getCurrentStatus()).getSurveyDefinition().getUri(), surveyInstance.getAltPid());
                                returnMap.put(JSON_SESSIONID, currentSessionId);
                                returnMap.put(JSON_SURVEY, recipient.getCurrentStatus());
                            }

                            //update participant and queue emails if we need to
                            surveyInstance.runCompletionPostProcessing(recipient, nextSessionId, (DatStatUtil) edc, alreadyCompleted, surveyConfig.isAnonymousSurvey(), surveyName);

                            //if the participant JUST exited, delete any emails we just queued
                            removeExitedParticipantEmails(participantId, surveyConfig);

                            return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
                        }
                    }
                } else {
                    throw new RuntimeException("Unable to find survey config for survey " + surveyName + ".");
                }
            } else {
                throw new RuntimeException("Survey name parameter is missing.");
            }
        }
        catch (Exception e) {
            logger.error(LOG_PREFIX + "Unable to process survey.", e);
            return new Result(500);
        }
    }

    private void removeExitedParticipantEmails(String participantId, SurveyConfig surveyConfig) {
        if (!surveyConfig.isAnonymousSurvey()) {
            Recipient recipient = ((DatStatUtil) edc).getSimpleParticipantInfoById(participantId);
            if (recipient.getDateExited() != null) {
                EmailRecord.removeUnsentEmails(recipient.getEmail());
            }
        }
    }

    private boolean terminatedNow(String participantId, SurveyConfig surveyConfig, SurveyService service, String sessionId) {
        boolean terminated = false;
        if (!surveyConfig.isAnonymousSurvey()) {
            Recipient recipient = ((DatStatUtil)edc).getSimpleParticipantInfoById(participantId);
            return terminatedNow(recipient, surveyConfig, service, sessionId);
        }
        return terminated;
    }

    private boolean terminatedNow(Recipient recipient, SurveyConfig surveyConfig, SurveyService service, String sessionId) {
        boolean terminated = false;
        if (recipient.getDateExited() != null) {
            service.terminateSurveyInstance((DatStatUtil)edc, surveyConfig.getSurveyClass(), sessionId);
            terminated = true;
        }
        logger.info("survey termination status = " + terminated);
        return terminated;
    }

    private Result terminatedResult(boolean patch, String sessionId, String surveyName) {
        if (!patch) {
            HashMap<String, String> returnMap = new HashMap<>();
            returnMap.put(JSON_SESSIONID, sessionId);
            returnMap.put(JSON_SURVEY, surveyName);
            return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
        }
        else {
            return new Result(200, "{\"status\":\"" + AbstractSurveyInstance.READONLY + "\"}");
        }
    }

    /**
     * Creates new survey if one doesn't already exists. Will return existing survey session id if survey already exists.
     */
    private String createSurveySession(String participantAltPid, SurveyConfig config, SurveyService service, Recipient recipient, Payload payload)
    {
        String sessionId = ((DatStatUtil)edc).getSingleSurveySessionViaUri(config.getSurveyDefinition().getUri(), participantAltPid);
        if (sessionId == null)
        {
            SurveyInstance surveyInstance = service.createSurveyInstance((DatStatUtil)edc, config.getSurveyDefinition(), config.getSurveyClass(), recipient, payload);
            sessionId = surveyInstance.getSurveySession().getSessionId();
            logger.info(LOG_PREFIX + "Survey session "+ sessionId +" created.");
        }
        else
        {
            logger.warn(LOG_PREFIX + "Unable to create new survey. Survey already exists for participant. Will return existing session id.");
        }
        return sessionId;
    }
}
