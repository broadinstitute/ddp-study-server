package org.broadinstitute.lddp.handlers;

import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.datstat.FollowUpSurveyRecord;
import org.broadinstitute.lddp.datstat.SurveyConfig;
import org.broadinstitute.lddp.datstat.SurveyService;
import org.broadinstitute.lddp.email.Recipient;
import org.broadinstitute.lddp.handlers.util.FollowUpSurvey;
import org.broadinstitute.lddp.handlers.util.Result;
import org.broadinstitute.lddp.util.EDCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ebaker on 11/16/17.
 */
public class FollowUpSurveyHandler extends AbstractRequestHandler<FollowUpSurvey> {

    private static final Logger logger = LoggerFactory.getLogger(FollowUpSurveyHandler.class);
    private static final String LOG_PREFIX = "PROCESS FOLLOW-UP REQUEST - ";
    public static final String ADD_NOW_CODE = "10";

    public FollowUpSurveyHandler(EDCClient edc, Config config) {
        super(FollowUpSurvey.class, edc, config);
    }

    @Override
    protected Result processRequest(@NonNull FollowUpSurvey followUpSurvey, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        logger.info(LOG_PREFIX + "Start processing request...");

        try {
            if (!pathParams.isEmpty()) {
                String surveyName = pathParams.get(":survey");

                logger.info(LOG_PREFIX + "Processing request for survey " + surveyName);

                SurveyConfig surveyConfig = ((DatStatUtil) edc).getSurveyConfigMap().get(surveyName);
                if (surveyConfig == null) {
                    throw new RuntimeException("Unable to find survey config for survey " + surveyName + ".");
                }
                else if (surveyConfig.getFollowUpType() == SurveyConfig.FollowUpType.NONE) {
                    throw new RuntimeException("Survey " + surveyName + " is not a follow-up survey.");
                }

                DatStatUtil datStatUtil = (DatStatUtil)edc;
                Recipient recipient = null;

                if (datStatUtil.datStatHasParticipantLists()) {
                    recipient = datStatUtil.getSimpleParticipantInfoByAltPid(followUpSurvey.getParticipantId());
                }
                else if ((followUpSurvey.getRecipient() != null)&&(followUpSurvey.getRecipient().isValid())) {
                    //only valid if we have an email address in it
                    recipient = followUpSurvey.getRecipient();
                }

                if ((recipient == null) || (recipient.getDateExited() != null)) {
                    if (datStatUtil.datStatHasParticipantLists()) {
                        logger.error(LOG_PREFIX + "Unable to find active participant using id = " + followUpSurvey.getParticipantId());
                        return new Result(404);
                    }
                    else {
                        throw new RuntimeException("Follow-up survey recipient data missing for participant with id = " + followUpSurvey.getParticipantId());
                    }
                }

                if (!followUpSurvey.isGenerateNow()) {
                    FollowUpSurveyRecord.add(recipient, surveyConfig, datStatUtil.datStatHasParticipantLists(), followUpSurvey.getTriggerId());
                    return new Result(200);
                }
                else {
                    HashMap<String, String> returnMap = addNow(recipient, surveyConfig, datStatUtil.datStatHasParticipantLists(), datStatUtil);
                    return new Result(200, new GsonBuilder().serializeNulls().create().toJson(returnMap));
                }
            }
            else
            {
                throw new RuntimeException("Survey is missing.");
            }
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "Error creating survey", e);
            return new Result(500);
        }
    }

    public static HashMap<String, String> addNow(@NonNull Recipient recipient, @NonNull SurveyConfig surveyConfig, boolean datStatHasParticipantLists,
                                                 @NonNull DatStatUtil datStatUtil) {
        logger.info(LOG_PREFIX + "Adding survey record NOW...");

        HashMap<String, String> returnMap = null;

        if (surveyConfig.getFollowUpType() != SurveyConfig.FollowUpType.NONREPEATING) {
            throw new RuntimeException("AddNow can only be used for nonrepeating surveys.");
        }

        FollowUpSurveyRecord record = FollowUpSurveyRecord.getRecord(surveyConfig.getSurveyPathName(), recipient.getId(), FollowUpSurveyRecord.NONREPEATING_SURVEY_INSTANCE, datStatHasParticipantLists);

        if (record == null) {
            FollowUpSurveyRecord.add(recipient, surveyConfig, datStatUtil.datStatHasParticipantLists(), -1L, true);
            record = FollowUpSurveyRecord.getRecord(surveyConfig.getSurveyPathName(), recipient.getId(), FollowUpSurveyRecord.NONREPEATING_SURVEY_INSTANCE, datStatHasParticipantLists);
            datStatUtil.createFollowUpSurvey(null, record, new SurveyService());
        }

        returnMap = datStatUtil.getSingleSurveyInfoViaSurvey(surveyConfig.getSurveyPathName(), recipient.getId(), FollowUpSurveyRecord.NONREPEATING_SURVEY_INSTANCE);


        if (returnMap == null) {
            throw new RuntimeException("Unable to find session for survey " + surveyConfig.getSurveyPathName() + " and participant " + recipient.getId() + " in DatStat.");
        }

        return returnMap;
    }
}