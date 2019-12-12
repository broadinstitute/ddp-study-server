package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.json.ActivityInstanceCreationPayload;
import org.broadinstitute.ddp.json.ActivityInstanceCreationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.service.ActivityInstanceCreationValidation;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class CreateActivityInstanceRoute extends ValidatedJsonInputRoute<ActivityInstanceCreationPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateActivityInstanceRoute.class);

    private final ActivityInstanceDao activityInstanceDao;

    /**
     * Instantiate CreateActivityInstanceRoute with proper daos.
     */
    public CreateActivityInstanceRoute(ActivityInstanceDao activityInstanceDao) {
        this.activityInstanceDao = activityInstanceDao;
    }

    @Override
    public Object handle(Request request, Response response, ActivityInstanceCreationPayload payload) throws
            Exception {
        String activityCode = payload.getActivityCode();
        String participantGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();

        LOG.info("Request to create instance of activity {} for user {}", activityCode, participantGuid);

        return TransactionWrapper.withTxn(handle -> {
            Long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        String msg = String.format("The study guid '%s' does not refer to a valid study", studyGuid);
                        LOG.warn(msg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.STUDY_NOT_FOUND, msg));
                    });

            ActivityInstanceCreationValidation validation = activityInstanceDao
                    .checkSuitabilityForActivityInstanceCreation(handle, activityCode, participantGuid, studyId);

            // check for max instances
            if (validation.hasTooManyInstances()) {
                ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.TOO_MANY_INSTANCES, null));
            }
            // apply any precondition pex
            if (validation.hasUnsatisfiedPrecondition()) {
                ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.UNSATISFIED_PRECONDITION, null));
            }

            Long studyActivityId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(studyId, activityCode).get();
            // todo: remove package qualifier when things are properly migrated
            String instanceGuid = handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                    .insertInstance(studyActivityId, operatorGuid, participantGuid, InstanceStatusType.CREATED, false)
                    .getGuid();
            handle.attach(DataExportDao.class).queueDataSync(participantGuid, studyGuid);
            LOG.info("Created activity instance {} for activity {} and user {}",
                    instanceGuid, activityCode, participantGuid);
            return new ActivityInstanceCreationResponse(instanceGuid);
        });

    }

}
