package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.json.FileDownloadResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.service.FileDownloadService;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Request;
import spark.Response;
import spark.Route;

import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
public class GetParticipantFileDownloadRoute implements Route {
    private static FileDownloadService fileDownloadService;

    public GetParticipantFileDownloadRoute(FileDownloadService service) {
        fileDownloadService = service;
    }

    @Override
    public Object handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String participantGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);
        String questionStableId = request.params(RouteConstants.PathParam.QUESTION_STABLE_ID);

        log.info("Fetching signed url for file download of activity instance {} and participant {} in study {} for Question: {})",
                instanceGuid, participantGuid, studyGuid, questionStableId);

        if (StringUtils.isEmpty(studyGuid) || StringUtils.isEmpty(participantGuid)
                || StringUtils.isEmpty(instanceGuid) || StringUtils.isEmpty(questionStableId)) {
            throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, "Required parameter(s) missing"));
        }

        return TransactionWrapper.withTxn(handle -> {
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            try {
                instanceDao.getActivityInstanceIdByGuid(handle, instanceGuid);
            } catch (NoSuchElementException e) {
                throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, "Instance not found"));
            }

            AnswerDao answerDao = handle.attach(AnswerDao.class);
            Optional<Answer> answerOpt = answerDao.findAnswerByInstanceGuidAndQuestionStableId(instanceGuid, questionStableId);
            if (!answerOpt.isPresent()) {
                String errorMsg = "Failed to find answer for participant: " + participantGuid + "instanceGuid: "
                        + instanceGuid + " and stableID: " + questionStableId;
                throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ANSWER_NOT_FOUND, errorMsg));
            } else {
                String fileName = answerOpt.get().getValue().toString();
                URL downloadUrl = fileDownloadService.getSignedURL(fileName, null);
                return new FileDownloadResponse(downloadUrl.toString());
            }
        });
    }
}
