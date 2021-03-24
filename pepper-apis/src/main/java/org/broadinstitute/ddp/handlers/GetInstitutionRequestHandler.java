package org.broadinstitute.ddp.handlers;

import lombok.NonNull;
import org.broadinstitute.ddp.datstat.DatStatUtil;
import org.broadinstitute.ddp.handlers.util.EmptyPayload;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;

import java.util.Map;
import com.typesafe.config.Config;
import spark.Response;

public class GetInstitutionRequestHandler extends AbstractRequestHandler<EmptyPayload>  {

    private static final Logger logger = LoggerFactory.getLogger(GetInstitutionRequestHandler.class);

    private static final String LOG_PREFIX = "PROCESS INSTITUTION REQUEST - ";

    public GetInstitutionRequestHandler(EDCClient edc, Config config){
        super(EmptyPayload.class, edc, config);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            logger.info(LOG_PREFIX + "Start processing request...");
            int lastId = (pathParams.isEmpty()) ? 0 : Integer.parseInt(pathParams.get(":id"));

            logger.info(LOG_PREFIX + "Retrieving requests after " + lastId);
            String json = edc.getInstitutionRequests(lastId);

            return new Result(200, json);
        }
        catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}
