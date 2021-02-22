package org.broadinstitute.ddp.route;

import java.util.Set;

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
import org.broadinstitute.ddp.json.ActivityInstanceCreationPayload;
import org.broadinstitute.ddp.json.ActivityInstanceCreationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class CreateActivityInstanceRoute extends ValidatedJsonInputRoute<ActivityInstanceCreationPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateActivityInstanceRoute.class);

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

        LOG.info("Request to create instance of activity {} in study {} for user {}"
                        + " and parent instance {} by operator {} (isStudyAdmin={})",
                activityCode, studyGuid, participantGuid, parentInstanceGuid, operatorGuid, isStudyAdmin);

        return TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, participantGuid, studyGuid);
            long studyId = found.getStudyDto().getId();
            long participantId = found.getUser().getId();

            var activityInstanceDao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceCreationValidation validation = activityInstanceDao
                    .checkSuitabilityForActivityInstanceCreation(studyId, activityCode, participantGuid)
                    .orElse(null);
            if (validation == null) {
                var err = new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND,
                        "Could not find creation validation information for activity " + activityCode);
                LOG.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            Long parentInstanceId = null;
            if (validation.getParentActivityCode() != null) {
                if (StringUtils.isBlank(parentInstanceGuid)) {
                    String msg = "Creating child nested activity instance requires parent instance guid";
                    LOG.warn(msg);
                    throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                            new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
                }

                ActivityInstanceDto parentInstanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                        response, handle, found.getUser(), found.getStudyDto(),
                        parentInstanceGuid, isStudyAdmin);

                if (!validation.getParentActivityCode().equals(parentInstanceDto.getActivityCode())) {
                    String msg = "Child activity's parent activity does not match provided parent instance's activity";
                    LOG.warn(msg);
                    throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST,
                            new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
                }

                ActivityDefStore activityStore = ActivityDefStore.getInstance();
                FormActivityDef parentActivity = ActivityInstanceUtil.getActivityDef(handle, activityStore, parentInstanceDto, studyGuid);
                if (!isStudyAdmin && ActivityInstanceUtil.isInstanceReadOnly(parentActivity, parentInstanceDto)) {
                    String msg = "Could not create child activity instance, parent instance is read-only";
                    LOG.warn(msg);
                    throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                            new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                }

                parentInstanceId = parentInstanceDto.getId();
            }

            // check for max instances
            if (validation.hasTooManyInstances()) {
                LOG.warn("Participant has {} instances which exceeds max allowed of {}",
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
            LOG.info("Created activity instance {} for activity {} and user {}",
                    instanceGuid, activityCode, participantGuid);

            return new ActivityInstanceCreationResponse(instanceGuid);
        });
    }

}
