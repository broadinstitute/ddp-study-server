package org.broadinstitute.lddp.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.typesafe.config.Config;
import org.broadinstitute.lddp.datstat.ParticipantFields;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;

public class GetParticipantRequestHandler extends AbstractRequestHandler<EmptyPayload>
{
    private static final Logger logger = LoggerFactory.getLogger(GetParticipantRequestHandler.class);
    private static final String LOG_PREFIX = "PROCESS PARTICIPANT REQUEST - ";

    public GetParticipantRequestHandler(EDCClient edc, Config config) {
        super(EmptyPayload.class, edc, config);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            String json;

            if (!pathParams.isEmpty()) {
                JsonElement participant = edc.getParticipantById(pathParams.get(":id"), ParticipantFields.values());
                if (participant == null) {
                    logger.error(LOG_PREFIX + "Unable to find participant using id = " + pathParams.get(":id"));
                    return new Result(404);
                }
                json = new Gson().toJson(participant);
            }
            else {
                JsonArray participants = edc.getAllParticipants(ParticipantFields.values());
                json = new Gson().toJson(participants);
            }
            return new Result(200, json);
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}
