package org.broadinstitute.lddp.handlers;

import com.auth0.jwt.interfaces.Claim;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.email.Recipient;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;
import org.broadinstitute.lddp.handlers.util.HandlerUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

public class GetParticipantSurveySessionIdHandler extends AbstractRequestHandler<EmptyPayload>
{
    private static final Logger logger = LoggerFactory.getLogger(GetParticipantSurveySessionIdHandler.class);

    public GetParticipantSurveySessionIdHandler(EDCClient edc, Config config) {
        super(EmptyPayload.class, edc, config);
    }

    @Override
    /**
     * Requires valid account tokens with altpids.
     */
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            logger.info("Start processing participant survey session id request...");

            Map<String, Claim> claims = SecurityHelper.verifyAndGetClaims(config.getString("portal.jwtSecret"), token);

            String participantAltPid = null;

            try {
                participantAltPid = SecurityHelper.getAltPidForAccountFromToken(claims);
            }
            catch (Exception ex) {
                return new Result(403, SecurityHelper.ResultType.AUTHORIZATION_ERROR.toString());
            }

            logger.info("Checking status for participant " + participantAltPid);

            // check if participant exists
            Recipient recipient = ((DatStatUtil)edc).getSimpleParticipantInfoByAltPid(participantAltPid);

            if (recipient == null) {
                logger.error("Unable to find participant using id = " + participantAltPid);
                return new Result(404);
            }

            HashMap<String, String> returnMap = new HashMap<>();
            returnMap.put(SurveySessionHandler.JSON_SESSIONID, null);

            String survey = pathParams.get(HandlerUtil.PATHPARAM_SURVEY);

            if (StringUtils.isBlank(survey)) {
                throw new RuntimeException("Survey parameter is required to determine survey session for altpid = " + participantAltPid);
            }

            String sessionId = ((DatStatUtil)edc).getExpectedSingleSurveySessionViaSurvey(survey, recipient.getId());
            returnMap.put(SurveySessionHandler.JSON_SESSIONID, sessionId);

            return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
        }
        catch (Exception ex) {
            logger.error("Error fetching request.", ex);
            return new Result(500);
        }
    }
}

