package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.service.FileUploadService.AuthorizeResultType.FILE_SIZE_EXCEEDS_MAXIMUM;
import static org.broadinstitute.ddp.service.FileUploadService.AuthorizeResultType.MIME_TYPE_NOT_ALLOWED;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
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
import spark.Request;
import spark.Response;

import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@AllArgsConstructor
public class CreateUserActivityUploadRoute extends ValidatedJsonInputRoute<CreateUserActivityUploadPayload> {
    private final FileUploadService service;
    private static final int MB_IN_BYTES = 1048576;

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

        log.info("Authorizing file upload URL for user {}, operator {} (isStudyAdmin={}), activity instance {}, study {}",
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
                    instanceDto.getStatusType().name(), activityDef.isWriteOnce(), instanceDto.getIsReadonly());
            if (!isStudyAdmin && isInstanceReadOnly) {
                String msg = "Activity instance " + instanceGuid + " is read-only, no file upload will be authorized";
                log.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                        new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
            }

            String questionStableId = payload.getQuestionStableId();
            QuestionDef questionDef = activityDef.getQuestionByStableId(questionStableId);
            FileQuestionDef fileQuestionDef;
            if (questionDef == null) {
                String msg = "Could not find question with stable id " + questionStableId;
                log.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.QUESTION_NOT_FOUND, msg));
            } else if (questionDef.getQuestionType() != QuestionType.FILE) {
                String msg = "Question " + questionStableId + " does not support file uploads";
                log.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(ErrorCodes.NOT_SUPPORTED, msg));
            } else {
                fileQuestionDef = (FileQuestionDef) questionDef;
            }

            boolean isQuestionReadOnly = QuestionUtil.isReadonly(handle, questionDef, instanceDto);
            if (!isStudyAdmin && isQuestionReadOnly) {
                String msg = "Question " + questionStableId + " is read-only, no file upload will be authorized";
                log.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                        new ApiError(ErrorCodes.QUESTION_IS_READONLY, msg));
            }

            User operatorUser = handle.attach(UserDao.class).findUserByGuid(operatorGuid)
                    .orElseThrow(() -> new DDPException("Could not find operator with guid " + operatorGuid));

            String prefix = String.format("%s/%s/%s/%s", studyGuid, getCurrentDate(), userGuid, instanceDto.getActivityCode());
            return service.authorizeUpload(
                    handle,
                    instanceDto.getStudyId(),
                    operatorUser.getId(),
                    instanceDto.getParticipantId(),
                    fileQuestionDef,
                    prefix,
                    payload.getMimeType(),
                    payload.getFileName(),
                    payload.getFileSize(),
                    payload.isResumable());
        });

        if (result.getAuthorizeResultType() != FileUploadService.AuthorizeResultType.OK) {
            String msg = null;
            if (result.getAuthorizeResultType() == FILE_SIZE_EXCEEDS_MAXIMUM) {
                msg = "File size exceeded maximum of " + bytesToMbs(result.getFileUploadSettings().getMaxFileSize()) + " MB-s";
            } else if (result.getAuthorizeResultType() == MIME_TYPE_NOT_ALLOWED) {
                msg = "Mime type not belongs to allowed list: " + result.getFileUploadSettings().getMimeTypes();
            }
            log.warn(msg);
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
        }

        FileUpload upload = result.getFileUpload();
        log.info("Authorized file upload with id={} for bucket={} blobName={}",
                upload.getId(), service.getUploadsBucket(), upload.getBlobName());

        response.status(HttpStatus.SC_CREATED);
        return new CreateUserActivityUploadResponse(upload.getGuid(), result.getSignedUrl().toString());
    }

    private long bytesToMbs(long maxFileSize) {
        return maxFileSize / MB_IN_BYTES;
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy_MM_dd").format(new Date());
    }
}
