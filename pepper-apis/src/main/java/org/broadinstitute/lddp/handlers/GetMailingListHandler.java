package org.broadinstitute.lddp.handlers;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.lddp.handlers.util.Contact;
import org.broadinstitute.lddp.handlers.util.EmptyPayload;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;
/**
 * Created by ebaker on 5/1/17.
 */
public class GetMailingListHandler extends AbstractRequestHandler<EmptyPayload> {

    private static final Logger logger = LoggerFactory.getLogger(GetMailingListHandler.class);

    private static final String LOG_PREFIX = "PROCESS MAILING LIST REQUEST - ";

    public GetMailingListHandler(Config config) {
        super(EmptyPayload.class, config);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            logger.info(LOG_PREFIX + "Start processing request...");

            return new Result(200, new Gson().toJson(Contact.getAllContactsFromDB()));
        }
        catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}
