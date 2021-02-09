package org.broadinstitute.ddp.route;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.CreateUserActivityUploadPayload;
import org.broadinstitute.ddp.json.CreateUserActivityUploadResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.FileUploadService;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.QuestionUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class CreateUserActivityUploadRoute extends ValidatedJsonInputRoute<CreateUserActivityUploadPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateUserActivityUploadRoute.class);

    private final FileUploadService service;

    public CreateUserActivityUploadRoute(FileUploadService service) {
        this.service = service;
    }

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, CreateUserActivityUploadPayload payload) throws Exception {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), userGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        LOG.info("Authorizing file upload URL for user {}, operator {} (isStudyAdmin={}), activity instance {}, study {}",
                userGuid, operatorGuid, isStudyAdmin, instanceGuid, studyGuid);

        FileUploadService.AuthorizeResult result = TransactionWrapper.withTxn(handle -> {
            ActivityInstanceDto instanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                    response, handle, userGuid, studyGuid, instanceGuid, isStudyAdmin);

            ActivityDefStore activityStore = ActivityDefStore.getInstance();
            ActivityDto activityDto = activityStore.findActivityDto(handle, instanceDto.getActivityId())
                    .orElseThrow(() -> new DDPException("Could not find activity dto for instance " + instanceGuid));
            ActivityVersionDto versionDto = activityStore
                    .findVersionDto(handle, instanceDto.getActivityId(), instanceDto.getCreatedAtMillis())
                    .orElseThrow(() -> new DDPException("Could not find activity version for instance " + instanceGuid));
            FormActivityDef activityDef = activityStore.findActivityDef(handle, studyGuid, activityDto, versionDto)
                    .orElseThrow(() -> new DDPException("Could not find activity definition for instance " + instanceGuid));

            boolean isInstanceReadOnly = ActivityInstanceUtil.isReadonly(
                    activityDef.getEditTimeoutSec(), instanceDto.getCreatedAtMillis(),
                    instanceDto.getStatusType().name(), activityDef.isWriteOnce(), instanceDto.getReadonly());
            if (!isStudyAdmin && isInstanceReadOnly) {
                String msg = "Activity instance " + instanceGuid + " is read-only, no file upload will be authorized";
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
            }

            String questionStableId = payload.getQuestionStableId();
            QuestionDef questionDef = activityDef.getQuestionByStableId(questionStableId);
            if (questionDef == null) {
                String msg = "Could not find question with stable id " + questionStableId;
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.QUESTION_NOT_FOUND, msg));
            } else if (questionDef.getQuestionType() != QuestionType.FILE) {
                String msg = "Question " + questionStableId + " does not support file uploads";
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.NOT_SUPPORTED, msg));
            }

            boolean isQuestionReadOnly = QuestionUtil.isReadonly(handle, questionDef, instanceDto);
            if (!isStudyAdmin && isQuestionReadOnly) {
                String msg = "Question " + questionStableId + " is read-only, no file upload will be authorized";
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.QUESTION_IS_READONLY, msg));
            }

            User operatorUser = handle.attach(UserDao.class).findUserByGuid(operatorGuid)
                    .orElseThrow(() -> new DDPException("Could not find operator with guid " + operatorGuid));

            String prefix = String.format("%s/%s/%s", studyGuid, userGuid, instanceDto.getActivityCode());
            return service.authorizeUpload(
                    handle, operatorUser.getId(), instanceDto.getParticipantId(), prefix,
                    payload.getMimeType(), payload.getFileName(), payload.getFileSize(), payload.isResumable());
        });

        if (result.isExceededSize()) {
            String msg = "File size exceeded maximum of " + service.getMaxFileSizeBytes() + " bytes";
            LOG.warn(msg);
            throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.NOT_SUPPORTED, msg));
        }

        FileUpload upload = result.getFileUpload();
        LOG.info("Authorized file upload with id={} for bucket={} blobName={}",
                upload.getId(), service.getUploadsBucket(), upload.getBlobName());

        response.status(HttpStatus.SC_CREATED);
        return new CreateUserActivityUploadResponse(upload.getGuid(), result.getSignedUrl().toString());
    }
}
