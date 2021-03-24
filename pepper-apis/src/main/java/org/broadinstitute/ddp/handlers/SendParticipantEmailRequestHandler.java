package org.broadinstitute.ddp.handlers;

import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.ddp.handlers.util.Payload;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;

/**
 * Handles the processing of participant email requests.
 */
public class SendParticipantEmailRequestHandler extends AbstractRequestHandler<Payload>
{
    private static final Logger logger = LoggerFactory.getLogger(SendParticipantEmailRequestHandler.class);

    private static final String LOG_PREFIX = "PROCESS PARTICIPANT EMAIL REQUEST - ";

    public static final String EMAIL_FIELD = "email";

    public SendParticipantEmailRequestHandler(EDCClient edc, Config config)
    {
        super(Payload.class, edc, config);
    }

    @Override
    protected Result processRequest(@NonNull Payload payload, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response)
    {
        logger.info(LOG_PREFIX + "Starting...");

        //if ANYTHING bad happens (participant doesn't exist or exception occurs return 200 for now still)
        try
        {
            String email = (String)payload.get(EMAIL_FIELD);

            if (edc.participantExists(email))
            {
                logger.info(LOG_PREFIX + "Participant exists.");
                edc.sendEmailToParticipant(email);
                logger.info(LOG_PREFIX + "Email queued for participant.");
            }
            else
            {
                logger.error(LOG_PREFIX + "Unable to find participant with email: " + email + ".");
            }
        }
        catch (Exception ex)
        {
            logger.error(LOG_PREFIX + "error: ", ex);
        }

        return new Result(200);
    }
}
