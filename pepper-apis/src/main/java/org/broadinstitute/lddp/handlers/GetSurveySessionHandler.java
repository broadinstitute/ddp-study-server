package org.broadinstitute.lddp.handlers;

import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.datstat.*;
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

import java.util.Map;

public class GetSurveySessionHandler extends AbstractRequestHandler<EmptyPayload>
{
    private static final Logger logger = LoggerFactory.getLogger(GetSurveySessionHandler.class);

    public GetSurveySessionHandler(EDCClient edc, Config config) {
        super(EmptyPayload.class, edc, config);
    }

    @Override
    protected Result processRequest(EmptyPayload value, QueryParamsMap queryParams, Map<String, String> pathParams,
                                    String requestMethod, String token, Response response) {
        String surveyName = pathParams.get(HandlerUtil.PATHPARAM_SURVEY);
        logger.info("Start processing " + requestMethod + " request with survey path name " + surveyName);
        if (StringUtils.isNotBlank(surveyName)) {
            try {
                SurveyConfig surveyConfig = ((DatStatUtil)edc).getSurveyConfigMap().get(surveyName);

                if (surveyConfig != null) {
                    SurveyService service = new SurveyService();
                    String sessionId = pathParams.get(HandlerUtil.PATHPARAM_SESSIONID);
                    SurveyInstance surveyInstance = service.fetchSurveyInstance((DatStatUtil)edc,
                            surveyConfig.getSurveyClass(), sessionId);

                    //make sure we have a good token for this particular survey
                    if (!SecurityHelper.hasValidSurveyAuthorization(config.getString("portal.jwtSecret"), token, surveyConfig.isAnonymousSurvey(),
                            surveyInstance.getAltPid(), (surveyInstance instanceof RecaptchaSecurity), surveyInstance.isAccountNeeded()))
                    {
                        return new Result(403, SecurityHelper.ResultType.AUTHORIZATION_ERROR.toString());
                    }

                    //terminate authenticated survey if participant has exited and survey hasn't been terminated yet (just in case termination failed)
                    if (!surveyConfig.isAnonymousSurvey()&&!surveyInstance.getSubmissionStatus().equals(SurveyInstance.SubmissionStatus.TERMINATED)) {
                        Recipient recipient = ((DatStatUtil)edc).getSimpleParticipantInfoByAltPid(surveyInstance.getAltPid());

                        //participant has been exited but survey was not terminated so terminate it and reload
                        if (recipient.getDateExited() != null) {
                            service.terminateSurveyInstance((DatStatUtil)edc, surveyConfig.getSurveyClass(), sessionId);
                            surveyInstance = service.fetchSurveyInstance((DatStatUtil)edc, surveyConfig.getSurveyClass(), sessionId);
                        }
                    }

                    return new Result(200, new GsonBuilder().serializeNulls().create().toJson(new SurveyToJsonUtil().convertUIFieldsToJson(surveyInstance)));
                }
            } catch (Exception e) {
                logger.error("ERROR - Something went wrong ", e);
                return new Result(500);
            }
        }
        logger.info("Survey name or config empty");
        return new Result(500);
    }
}

