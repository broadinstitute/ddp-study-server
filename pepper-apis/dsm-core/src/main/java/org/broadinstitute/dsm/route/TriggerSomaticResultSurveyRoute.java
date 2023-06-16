package org.broadinstitute.dsm.route;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.SomaticResultUpload;
import org.broadinstitute.dsm.db.SurveyTrigger;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.model.SomaticResultTriggerActivityPayload;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.SomaticResultUploadService;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class TriggerSomaticResultSurveyRoute extends RequestHandler {
    private final SomaticResultUploadService service;
    private static final Logger logger = LoggerFactory.getLogger(TriggerSomaticResultSurveyRoute.class);
    private static final String DEFAULT_TRIGGER_COMMENT = "Somatic results shared with participant.";
    protected static final String REQUIRED_SURVEY_CREATION_ROLE = "survey_creation";
    protected static final String REQUIRED_UPLOAD_ROR_ROLE = "upload_ror_file";

    public TriggerSomaticResultSurveyRoute(SomaticResultUploadService service) {
        this.service = service;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm = queryParams.get(RoutePath.REALM).value();
        if (StringUtils.isBlank(realm)) {
            throw new DSMBadRequestException("realm is a required query parameter.");
        }
        if (!isAuthorized(userId, realm)) {
            throw new AuthorizationException(UserErrorMessages.NO_RIGHTS);
        }
        SomaticResultTriggerRequestPayload requestPayload = getRequestPayload(queryParams, request);
        SomaticResultUpload resultUpload = service.getSomaticResultByIdPtptAndRealm(requestPayload.getSomaticDocumentId(),
                requestPayload.getParticipantId(), realm);
        if (resultUpload != null) {
            DDPInstance instance = DDPInstance.getDDPInstance(realm);
            long triggerId = buildTriggerComment(userId, DEFAULT_TRIGGER_COMMENT);
            SomaticResultTriggerActivityPayload payloadToSend = new SomaticResultTriggerActivityPayload(
                    requestPayload.getParticipantId(), triggerId, resultUpload.getBucket(), resultUpload.getBlobPath());
            return DDPRequestUtil.triggerFollowupSurvey(instance, payloadToSend, requestPayload.getSurveyName());
        } else {
            throw new DSMBadRequestException("Bad somatic document id.  Not triggering followup");
        }
    }

    private long buildTriggerComment(String userIdRequest, String comment) {
        long currentTime = System.currentTimeMillis();
        try {
            return addTriggerCommentIntoDB(userIdRequest, comment, currentTime);
        } catch (Exception ex) {
            throw new DsmInternalError("Error encountered adding trigger and comment, please contact a DSM developer.");
        }
    }

    //this is modeled after the Query parameters used in TriggerSurveyRoute.java because we want this endpoint
    //to be similar to that one, but add the support for the required documentId parameter that we need to look
    //up the correct somatic file that was uploaded.
    private SomaticResultTriggerRequestPayload getRequestPayload(QueryParamsMap queryParams, Request request) {
        SomaticResultTriggerRequestPayload result = new SomaticResultTriggerRequestPayload();
        if (queryParams.value("surveyName") != null) {
            result.setSurveyName(queryParams.get("surveyName").value());
        } else {
            throw new DSMBadRequestException("No surveyName query param was sent");
        }
        if (!result.getSurveyName().equalsIgnoreCase("SOMATIC_RESULTS")) {
            throw new DSMBadRequestException("This route only works with SOMATIC_RESULTS surveys.");
        }
        if (queryParams.value("surveyType") != null) {
            result.setSurveyType(queryParams.get("surveyType").value());
        } else {
            throw new DSMBadRequestException("No surveyType query param was sent");
        }
        if (!result.getSurveyType().equalsIgnoreCase("REPEATING")) {
            throw new DSMBadRequestException("This route only supports REPEATING types");
        }
        try {
            JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
            String ddpParticipantId = jsonObject.getAsJsonObject().get("participantId").getAsString();
            int somaticDocumentId = jsonObject.getAsJsonObject().get(ESObjectConstants.SOMATIC_DOCUMENT_ID).getAsInt();
            result.setParticipantId(ddpParticipantId);
            result.setSomaticDocumentId(somaticDocumentId);
        } catch (Exception ex) {
            throw new DSMBadRequestException("Bad posted content.  Should be an object with participantId and somaticResultId");
        }

        return result;
    }

    private long addTriggerCommentIntoDB(@NonNull String userId, @NonNull String reason, long currentTime) {
        long surveyTriggerId;
        try {
            surveyTriggerId = SurveyTrigger.insertTrigger(userId, reason, currentTime);
        } catch (Exception e) {
            logger.error("Error inserting trigger for user {} with reason {}", userId, reason);
            throw new DsmInternalError(e.getMessage());
        }

        logger.info("Entered survey trigger reason into db w/ id {}", surveyTriggerId);
        return surveyTriggerId;
    }

    private boolean isAuthorized(String userId, String realm) {
        return (UserUtil.checkUserAccess(userId, realm, REQUIRED_SURVEY_CREATION_ROLE)
                &&  UserUtil.checkUserAccess(userId, realm, REQUIRED_UPLOAD_ROR_ROLE));
    }

    @Data
    private static class SomaticResultTriggerRequestPayload {
        private String surveyName;
        private String surveyType;
        private boolean triggerAgain;
        private String participantId;
        private int somaticDocumentId;
    }
}
