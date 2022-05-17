package org.broadinstitute.ddp.route;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceCreationValidation;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.ActivityInstanceCreationPayload;
import org.broadinstitute.ddp.json.ActivityInstanceCreationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.QuestionUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;

@Slf4j
public class CreateActivityInstanceRoute extends ValidatedJsonInputRoute<ActivityInstanceCreationPayload> {
    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, ActivityInstanceCreationPayload payload) throws Exception {
        String participantGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), participantGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        String activityCode = payload.getActivityCode();
        String parentInstanceGuid = payload.getParentInstanceGuid();

        log.info("Request to create instance of activity {} in study {} for user {}"
                        + " and parent instance {} by operator {} (isStudyAdmin={})",
                activityCode, studyGuid, participantGuid, parentInstanceGuid, operatorGuid, isStudyAdmin);

        return TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, participantGuid, studyGuid);
            long studyId = found.getStudyDto().getId();
            long participantId = found.getUser().getId();

            var activityInstanceDao = handle.attach(ActivityInstanceDao.class);

            createMutexLock(activityCode, studyId, participantId, activityInstanceDao);

            ActivityInstanceCreationValidation validation = activityInstanceDao
                    .checkSuitabilityForActivityInstanceCreation(studyId, activityCode, participantId)
                    .orElse(null);
            if (validation == null) {
                String msg = "Could not find creation validation information for activity " + activityCode;
                warnAndHalt(response, HttpStatus.SC_NOT_FOUND, ErrorCodes.ACTIVITY_NOT_FOUND, msg);
                return null;
            }

            ActivityInstanceCreationResponse res = new ActivityInstanceCreationResponse();

            Long parentInstanceId = null;
            if (validation.getParentActivityCode() != null) {
                parentInstanceId = findAndCheckParentInstance(
                        handle, response, validation.getParentActivityCode(), parentInstanceGuid,
                        found.getUser(), found.getStudyDto(), isStudyAdmin);
            }

            // check for max instances
            if (validation.hasTooManyInstances()) {
                log.warn("Participant has {} instances which exceeds max allowed of {}",
                        validation.getNumInstancesForUser(), validation.getMaxInstancesPerUser());
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                        new ApiError(ErrorCodes.TOO_MANY_INSTANCES, null));
            }

            long studyActivityId = validation.getActivityId();
            if (validation.isHideExistingInstancesOnCreation()) {
                activityInstanceDao.bulkUpdateIsHiddenByActivityIds(participantId, true, Set.of(studyActivityId));
            }

            String instanceGuid = activityInstanceDao
                    .insertInstance(studyActivityId, operatorGuid, participantGuid, parentInstanceId)
                    .getGuid();
            handle.attach(DataExportDao.class).queueDataSync(participantGuid, studyGuid);
            log.info("Created activity instance {} for activity {} and user {}",
                    instanceGuid, activityCode, participantGuid);
            res.setInstanceGuid(instanceGuid);
            res.setBlockVisibilities(QuestionUtil.getBlockVisibility(handle,
                        response, parentInstanceGuid, found.getUser(), found.getStudyDto(), operatorGuid, isStudyAdmin));
            return res;
        });
    }

    // Insert or update row for which this transaction will have to hold a lock before proceeding.
    // This enforces that for a given activity and participant only one transaction is allowed to execute
    // this code at a time.
    // Lessens chance of deadlocks and ensures the counts of activity instances for a given activity are
    // accurate so we can enforce max number of activity instances accurately
    private void createMutexLock(String activityCode, long studyId, long participantId, ActivityInstanceDao activityInstanceDao) {
        activityInstanceDao.upsertActivityInstanceCreationMutex(participantId, studyId, activityCode);
    }

    private void warnAndHalt(Response response, int status, String code, String message) {
        log.warn(message);
        throw ResponseUtil.haltError(response, status, new ApiError(code, message));
    }

    private Long findAndCheckParentInstance(Handle handle, Response response,
                                            String expectedParentActivityCode, String parentInstanceGuid,
                                            User participantUser, StudyDto studyDto, boolean isStudyAdmin) {
        if (StringUtils.isBlank(parentInstanceGuid)) {
            String msg = "Creating child nested activity instance requires parent instance guid";
            warnAndHalt(response, HttpStatus.SC_BAD_REQUEST, ErrorCodes.BAD_PAYLOAD, msg);
            return null;
        }

        ActivityInstanceDto parentInstanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                response, handle, participantUser, studyDto,
                parentInstanceGuid, isStudyAdmin);

        if (!expectedParentActivityCode.equals(parentInstanceDto.getActivityCode())) {
            String msg = "Child activity's parent activity does not match provided parent instance's activity";
            warnAndHalt(response, HttpStatus.SC_BAD_REQUEST, ErrorCodes.BAD_PAYLOAD, msg);
            return null;
        }

        ActivityDefStore activityStore = ActivityDefStore.getInstance();
        FormActivityDef parentActivity = ActivityInstanceUtil
                .getActivityDef(handle, activityStore, parentInstanceDto, studyDto.getGuid());
        if (!isStudyAdmin && ActivityInstanceUtil.isInstanceReadOnly(parentActivity, parentInstanceDto)) {
            String msg = "Could not create child activity instance, parent instance is read-only";
            warnAndHalt(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg);
            return null;
        }

        return parentInstanceDto.getId();
    }
}
