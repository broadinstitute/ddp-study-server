package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.handlers.util.ParticipantSurveyInfo;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.ddp.handlers.util.SimpleFollowUpSurvey;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.SurveyTrigger;
import org.broadinstitute.dsm.exception.SurveyNotCreated;
import org.broadinstitute.dsm.model.ParticipantSurveyStatusResponse;
import org.broadinstitute.dsm.model.ParticipantSurveyUploadObject;
import org.broadinstitute.dsm.model.ParticipantSurveyUploadResponse;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.*;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

import javax.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class TriggerSurveyRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(TriggerSurveyRoute.class);

    private static final String NO_SURVEY_STATUS = "NO_SURVEY_STATUS";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String userIdRequest = UserUtil.getUserId(request);
        if (request.requestMethod().equals(RoutePath.RequestMethod.GET.toString())) {
            String realm = request.params(RequestParameter.REALM);
            if (UserUtil.checkUserAccess(realm, userId, "survey_creation", userIdRequest)) {
                if (StringUtils.isNotBlank(realm)) {
                    DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.SURVEY_STATUS_ENDPOINTS);
                    QueryParamsMap queryParams = request.queryMap();
                    String selectedSurvey = "";
                    //follow up survey status
                    if (queryParams.value("surveyName") != null) {
                        selectedSurvey = queryParams.get("surveyName").value();
                        if (instance.isHasRole()) {
                            Map<String, SurveyTrigger> surveyTriggers = SurveyTrigger.getSurveyTriggers();
                            List<ParticipantSurveyStatusResponse> surveyInfoReasons = new ArrayList<>();
                            List<ParticipantSurveyInfo> surveyInfos = DDPRequestUtil.getFollowupSurveysStatus(instance, selectedSurvey);
                            for (ParticipantSurveyInfo surveyInfo : surveyInfos) {
                                ParticipantSurveyStatusResponse infoReason = new ParticipantSurveyStatusResponse(surveyInfo);
                                if (surveyInfo.getTriggerId() >= 1) {
                                    String surveyTriggerId = Long.toString(surveyInfo.getTriggerId());
                                    if (surveyTriggers.get(surveyTriggerId) != null) {
                                        SurveyTrigger trigger = surveyTriggers.get(surveyTriggerId);
                                        infoReason.setReason(trigger.getReason());
                                        infoReason.setUser(trigger.getUser());
                                        infoReason.setTriggeredDate(trigger.getTriggeredDate());
                                    }
                                }
                                else if (surveyInfo.getTriggerId() == -1) {
                                    infoReason.setReason("Was not triggered through DSM");
                                }
                                else if (surveyInfo.getTriggerId() == -2) {
                                    infoReason.setReason("Was triggered per Gen2");
                                }
                                surveyInfoReasons.add(infoReason);
                            }
                            return surveyInfoReasons;
                        }
                        else {
                            return new Result(200, NO_SURVEY_STATUS);
                        }
                    }
                    else {
                        //follow up surveys
                        return DDPRequestUtil.getFollowupSurveys(instance);
                    }
                }
                else {
                    throw new RuntimeException("Realm was missing");
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        //trigger follow up survey
        if (request.requestMethod().equals(RoutePath.RequestMethod.POST.toString())) {
            QueryParamsMap queryParams = request.queryMap();
            String realm;
            DDPInstance instance;
            if (queryParams.value(RoutePath.REALM) != null) {
                realm = queryParams.get(RoutePath.REALM).value();
                instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.SURVEY_STATUS_ENDPOINTS);
            }
            else {
                throw new RuntimeException("No realm query param was sent");
            }
            if (UserUtil.checkUserAccess(realm, userId, "survey_creation", userIdRequest)) {
                String surveyName;
                if (queryParams.value("surveyName") != null) {
                    surveyName = queryParams.get("surveyName").value();
                }
                else {
                    throw new RuntimeException("No surveyName query param was sent");
                }
                String surveyType;
                if (queryParams.value("surveyType") != null) {
                    surveyType = queryParams.get("surveyType").value();
                }
                else {
                    throw new RuntimeException("No surveyType query param was sent");
                }
                Boolean isFileUpload;
                if (queryParams.value("isFileUpload") != null) {
                    isFileUpload = queryParams.get("isFileUpload").booleanValue();
                }
                else {
                    throw new RuntimeException("No isFileUpload query param was sent");
                }

                boolean triggerAgainQueryParam = false;
                if (queryParams.value("triggerAgain") != null) {
                    triggerAgainQueryParam = queryParams.get("triggerAgain").booleanValue();
                }
                final boolean triggerAgain = triggerAgainQueryParam;

                if (instance != null) {
                    long currentTime = System.currentTimeMillis();
                    List<ParticipantSurveyInfo> surveyInfos = null;
                    String comment = null;
                    Long surveyTriggerId = null;
                    if (instance.isHasRole()) {
                        if (queryParams.value("comment") != null) {
                            comment = queryParams.get("comment").value();
                        }
                        else {
                            throw new RuntimeException("No comment query param was sent");
                        }
                        surveyInfos = DDPRequestUtil.getFollowupSurveysStatus(instance, surveyName);
                        surveyTriggerId = addTriggerCommentIntoDB(userIdRequest, comment, currentTime);
                    }
                    if (isFileUpload || triggerAgain) {
                        List<ParticipantSurveyUploadObject> participantList = null;
                        if (triggerAgain) { //already participants and no file
                            String requestBody = request.body();
                            participantList = Arrays.asList(new Gson().fromJson(requestBody, ParticipantSurveyUploadObject[].class));
                        }
                        else {
                            if (isFileUpload) {
                                HttpServletRequest rawRequest = request.raw();
                                String content = SystemUtil.getBody(rawRequest);
                                participantList = ParticipantSurveyStatusResponse.isFileValid(instance, content);
                            }
                        }
                        logger.info(participantList.size() + " Participants were uploaded to trigger surveys");
                        List<ParticipantSurveyUploadObject> failed = new ArrayList<>();
                        List<ParticipantSurveyUploadObject> alreadyUploaded = new ArrayList<>();
                        for (ParticipantSurveyUploadObject participant : participantList) {
                            if (triggerAgain) {
                                if (!triggerSurvey(instance, participant, surveyTriggerId, surveyName, instance.isHasRole())) {
                                    failed.add(participant);
                                }
                            }
                            else {
                                boolean alreadyTriggered = false;
                                if (surveyInfos != null) {
                                    for (ParticipantSurveyInfo inf0 : surveyInfos) {
                                        if (inf0.getParticipantId().equals(participant.getDDPParticipantID())) {
                                            alreadyTriggered = true;
                                            break;
                                        }
                                    }
                                }
                                if (!alreadyTriggered) {
                                    if (!triggerSurvey(instance, participant, surveyTriggerId, surveyName, instance.isHasRole())) {
                                        failed.add(participant);
                                    }
                                }
                                else {
                                    alreadyUploaded.add(participant);
                                }
                            }
                        }
                        return new ParticipantSurveyUploadResponse(failed, alreadyUploaded);
                    }
                    else {
                        JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
                        String ddpParticipantId = jsonObject.getAsJsonObject().get("participantId").getAsString();
                        SimpleFollowUpSurvey followUpSurvey = null;
                        if ("NONREPEATING".equals(surveyType)) {
                            boolean alreadyTriggered = false;
                            if (surveyInfos != null) {
                                for (ParticipantSurveyInfo inf0 : surveyInfos) {
                                    if (inf0.getParticipantId().equals(ddpParticipantId)) {
                                        alreadyTriggered = true;
                                        break;
                                    }
                                }
                            }
                            if (alreadyTriggered) {
                                List<ParticipantSurveyUploadObject> failed = new ArrayList<>();
                                List<ParticipantSurveyUploadObject> alreadyUploaded = new ArrayList<>();
                                alreadyUploaded.add(new ParticipantSurveyUploadObject(ddpParticipantId));
                                return new ParticipantSurveyUploadResponse(failed, alreadyUploaded);
                            }
                        }
                        followUpSurvey = new SimpleFollowUpSurvey(ddpParticipantId, surveyTriggerId);
                        if (followUpSurvey != null) {
                            return DDPRequestUtil.triggerFollowupSurvey(instance, followUpSurvey, surveyName);
                        }
                    }
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        return new Result(500);
    }

    private long addTriggerCommentIntoDB(@NonNull String userId, @NonNull String reason, @NonNull long currentTime) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.INSERT_SURVEY_TRIGGER), Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, reason);
                stmt.setLong(2, currentTime);
                stmt.setString(3, userId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            long surveyTriggerId = rs.getLong(1);
                            logger.info("Entered survey trigger reason into db w/ id " + surveyTriggerId);
                            dbVals.resultValue = surveyTriggerId;
                        }
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Error getting id of new survey trigger reason ", e);
                    }
                }
                else {
                    throw new RuntimeException("Something went wrong entering survey trigger reason into db");
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            logger.error("Couldn't enter survey trigger reason into db ", results.resultException);
        }
        return (long) results.resultValue;
    }

    private boolean triggerSurvey(@NonNull DDPInstance instance, @NonNull ParticipantSurveyUploadObject participant, Long surveyTriggerId,
                                  @NonNull String surveyName, boolean hasSurveyStatusEndpoint) {
        SimpleFollowUpSurvey survey = null;
        if (hasSurveyStatusEndpoint) {
            survey = new SimpleFollowUpSurvey(participant.getDDPParticipantID(), surveyTriggerId);
        }
        else {
            survey = new SimpleFollowUpSurvey(participant.getDDPParticipantID());
        }
        try {
            if (DDPRequestUtil.triggerFollowupSurvey(instance, survey, surveyName).getCode() != 200) {
                return false;
            }
        }
        catch (SurveyNotCreated e) {
            return false;
        }
        return true;
    }
}
