package org.broadinstitute.ddp.handlers;

import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.ddp.datstat.DatStatUtil;
import org.broadinstitute.ddp.datstat.FollowUpSurveyRecord;
import org.broadinstitute.ddp.datstat.SurveyInstance;
import org.broadinstitute.ddp.datstat.SurveyService;
import org.broadinstitute.ddp.email.EmailRecord;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.handlers.util.EmptyPayload;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.util.EDCClient;
import org.broadinstitute.ddp.util.MedicalInfoCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.Map;

public class ExitParticipantHandler <T extends SurveyInstance> extends AbstractRequestHandler<EmptyPayload>  {

    private static final Logger logger = LoggerFactory.getLogger(ExitParticipantHandler.class);

    private static final String LOG_PREFIX = "PROCESS EXIT PARTICIPANT REQUEST - ";

    public ExitParticipantHandler(EDCClient edc, Config config) {
        super(EmptyPayload.class, edc, config);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        try {
            logger.info(LOG_PREFIX + "Start processing request...");

            DatStatUtil datStatUtil = (DatStatUtil)edc;

            String altPid = pathParams.get(":id");

            String participantId = datStatUtil.getParticipantIdByAltPid(altPid);

            if (participantId == null)
            {
                logger.error(LOG_PREFIX + "Unable to find participant using id = " + altPid);
                return new Result(404);
            }

            logger.info(LOG_PREFIX + "Exiting participant " + altPid + "...");

            //1) set participant exit field
            datStatUtil.setParticipantExitedViaId(participantId);

            //2) remove pending follow-up surveys
            FollowUpSurveyRecord.removeUnprocessedSurveys(altPid);

            //3) get all survey sessions and terminate them
            SurveyService surveyService = new SurveyService();
            Map<String, Class<T>> sessions = datStatUtil.getAllSurveySessionsForParticipant(altPid);

            for(Map.Entry entry: sessions.entrySet()) {
                surveyService.terminateSurveyInstance(datStatUtil, (Class<T>)entry.getValue(), entry.getKey().toString());
            }

            //4) remove all unsent emails associated with participant
            Recipient recipient = datStatUtil.getSimpleParticipantInfoById(participantId);
            EmailRecord.removeUnsentEmails(recipient.getEmail());

            return new Result(200);
        }
        catch (Exception e) {
            logger.error(LOG_PREFIX + "Error fetching request.", e);
            return new Result(500);
        }
    }
}
