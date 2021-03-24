package org.broadinstitute.ddp.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.exception.DatStatKitRequestIdException;
import org.broadinstitute.ddp.handlers.util.EmptyPayload;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;

public class GetKitRequestHandler extends AbstractRequestHandler<EmptyPayload>
{
    private static final Logger logger = LoggerFactory.getLogger(GetKitRequestHandler.class);
    private static final String LOG_PREFIX = "PROCESS KIT REQUEST - ";

    public GetKitRequestHandler(EDCClient edc, Config config){
        super(EmptyPayload.class, edc, config);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            JsonArray reqDetails;
            //todo EOB - two getKitRequestsDetails methods should be refactored in DatStatUtil for core to remove code duplication logic at some point!!
            if (!pathParams.isEmpty()) {
                reqDetails = edc.getKitRequestsDetails(pathParams.get(":id"));
            }
            else {
                reqDetails = edc.getKitRequestsDetails();
            }
            return new Result(200, new Gson().toJson(reqDetails));
        } catch (DatStatKitRequestIdException kitEx) {
            logger.error(LOG_PREFIX + "Unable to find request id " + pathParams.get(":id"), kitEx);
            return new Result(404);
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}

