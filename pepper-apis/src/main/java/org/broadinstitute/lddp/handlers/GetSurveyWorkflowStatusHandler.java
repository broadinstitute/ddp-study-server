package org.broadinstitute.lddp.handlers;

import com.auth0.jwt.interfaces.Claim;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.email.Recipient;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

public class GetSurveyWorkflowStatusHandler extends AbstractRequestHandler<EmptyPayload>
{
    private static final Logger logger = LoggerFactory.getLogger(GetSurveyWorkflowStatusHandler.class);

    public GetSurveyWorkflowStatusHandler(EDCClient edc, Config config) {
        super(EmptyPayload.class, edc, config);
    }

    @Override
    /**
     * Requires valid account tokens with altpids.
     */
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            logger.info("Start processing participant survey workflow status request...");

            Map<String, Claim> claims = SecurityHelper.verifyAndGetClaims(config.getString("portal.jwtSecret"), token);
            String participantAltPid = SecurityHelper.getAltPidForAccountFromToken(claims);

            logger.info("Checking workflow status for participant " + participantAltPid);

            // check if participant exists
            Recipient recipient = ((DatStatUtil)edc).getSimpleParticipantInfoByAltPid(participantAltPid);

            if (recipient == null) {
                logger.error("Unable to find participant using id = " + participantAltPid);
                return new Result(404);
            }

            HashMap<String, String> returnMap = new HashMap<>();
            returnMap.put(SurveySessionHandler.JSON_SESSIONID, null);
            returnMap.put(SurveySessionHandler.JSON_SURVEY, DatStatUtil.STATUS_DONE);

            String currentSurvey = recipient.getCurrentStatus();
            if (StringUtils.isBlank(currentSurvey)||currentSurvey.equals(DatStatUtil.CURRENT_STATUS_UNKNOWN)) {
                throw new RuntimeException("Invalid status detected for participant with altpid = " + participantAltPid + ".");
            }
            else if (!currentSurvey.equals(DatStatUtil.STATUS_DONE)) {
                String currentSessionId = ((DatStatUtil)edc).getSingleSurveySessionViaSurvey(currentSurvey, recipient.getId());
                returnMap.put(SurveySessionHandler.JSON_SESSIONID, currentSessionId);
                returnMap.put(SurveySessionHandler.JSON_SURVEY, currentSurvey);
            }
            return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
        }
        catch (Exception ex) {
            logger.error("Error fetching request.", ex);
            return new Result(500);
        }
    }
}

