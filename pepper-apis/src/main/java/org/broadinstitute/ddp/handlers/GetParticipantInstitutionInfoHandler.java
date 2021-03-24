package org.broadinstitute.ddp.handlers;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.handlers.util.EmptyPayload;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;

public class GetParticipantInstitutionInfoHandler extends AbstractRequestHandler<EmptyPayload>  {

    private static final Logger logger = LoggerFactory.getLogger(GetParticipantInstitutionInfoHandler.class);

    private static final String LOG_PREFIX = "PROCESS INSTITUTION INFO REQUEST - ";

    private String surveyId;

    public GetParticipantInstitutionInfoHandler(EDCClient edc, Config appProps){
        super(EmptyPayload.class, edc, appProps);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            logger.info(LOG_PREFIX + "Start processing request...");

            String json = edc.getParticipantInstitutionInfo();

            return new Result(200, json);
        }
        catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}