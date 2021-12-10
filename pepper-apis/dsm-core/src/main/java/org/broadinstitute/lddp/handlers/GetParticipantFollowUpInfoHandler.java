package org.broadinstitute.lddp.handlers;

import com.typesafe.config.Config;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;

public class GetParticipantFollowUpInfoHandler extends AbstractRequestHandler<EmptyPayload> {

    private static final Logger logger = LoggerFactory.getLogger(GetParticipantFollowUpInfoHandler.class);

    private static final String LOG_PREFIX = "PROCESS FOLLOW-UP INFO REQUEST - ";

    public GetParticipantFollowUpInfoHandler(EDCClient edc, Config conf) {
        super(EmptyPayload.class, edc, conf);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        logger.info(LOG_PREFIX + "Start processing request...");
        try {
            String json = null;
            if (!pathParams.isEmpty()) {
                String surveyName = pathParams.get(":survey");

                logger.info(LOG_PREFIX + "Processing request for survey " + surveyName);

                json = edc.getParticipantFollowUpInfo(surveyName);
            }
            else {
                throw new RuntimeException("Survey is missing.");
            }
            return new Result(200, json);
        }
        catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}