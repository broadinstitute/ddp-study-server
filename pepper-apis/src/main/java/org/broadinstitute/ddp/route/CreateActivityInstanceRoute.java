package org.broadinstitute.ddp.route;

import java.util.Set;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceCreationValidation;
import org.broadinstitute.ddp.json.ActivityInstanceCreationPayload;
import org.broadinstitute.ddp.json.ActivityInstanceCreationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
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
        String activityCode = payload.getActivityCode();
        String participantGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();

        LOG.info("Request to create instance of activity {} in study {} for user {} by operator {}",
                activityCode, studyGuid, participantGuid, operatorGuid);

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
                throw ResponseUtil.haltError(response, 404, err);
            }

            // check for max instances
            if (validation.hasTooManyInstances()) {
                LOG.warn("Participant has {} instances which exceeds max allowed of {}",
                        validation.getNumInstancesForUser(), validation.getMaxInstancesPerUser());
                throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.TOO_MANY_INSTANCES, null));
            }

            long studyActivityId = validation.getActivityId();
            if (validation.isHideExistingInstancesOnCreation()) {
                activityInstanceDao.bulkUpdateIsHiddenByActivityIds(participantId, true, Set.of(studyActivityId));
            }

            String instanceGuid = activityInstanceDao
                    .insertInstance(studyActivityId, operatorGuid, participantGuid, InstanceStatusType.CREATED, null)
                    .getGuid();
            handle.attach(DataExportDao.class).queueDataSync(participantGuid, studyGuid);
            LOG.info("Created activity instance {} for activity {} and user {}",
                    instanceGuid, activityCode, participantGuid);

            return new ActivityInstanceCreationResponse(instanceGuid);
        });
    }

}
