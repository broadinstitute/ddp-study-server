package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.dsm.TriggerActivityPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class DsmTriggerOnDemandActivityRoute extends ValidatedJsonInputRoute<TriggerActivityPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(DsmTriggerOnDemandActivityRoute.class);

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, TriggerActivityPayload payload) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String activityCode = request.params(PathParam.ACTIVITY_CODE);
        String participantGuidOrLegacyAltPid = payload.getParticipantGuid();

        LOG.info("Attempting to trigger on-demand activity for study guid {}, activity code {}, participant guid {}",
                studyGuid, activityCode, participantGuidOrLegacyAltPid);

        TransactionWrapper.useTxn(handle -> {
            long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                        LOG.warn(err.getMessage());
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                        return -1L;     // Not reached
                    });

            ActivityDto activityDto = handle.attach(JdbiActivity.class)
                    .findActivityByStudyIdAndCode(studyId, activityCode)
                    .orElseGet(() -> {
                        ApiError err = new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, String.format(
                                "Could not find activity with code %s for study with guid %s", activityCode, studyGuid));
                        LOG.warn(err.getMessage());
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                        return null;    // Not reached
                    });

            UserDto userDto = handle.attach(JdbiUser.class).findByLegacyAltPidIfNotFoundByUserGuid(participantGuidOrLegacyAltPid);
            if (userDto == null) {
                ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND, String.format(
                        "Could not find participant with guid %s", participantGuidOrLegacyAltPid));
                LOG.warn(err.getMessage());
                ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                return;
            }
            String participantGuid = userDto.getUserGuid();

            int instanceCount = handle.attach(JdbiActivityInstance.class)
                    .getNumActivitiesForParticipant(activityDto.getActivityId(), userDto.getUserId());

            if (activityDto.getMaxInstancesPerUser() != null && instanceCount >= activityDto.getMaxInstancesPerUser()) {
                ApiError err = new ApiError(ErrorCodes.TOO_MANY_INSTANCES, String.format(
                        "Exceeded maximum number of allowed activity instances for participant %s", participantGuid));
                LOG.warn(err.getMessage());
                ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                return;
            }

            ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activityDto.getActivityId(), participantGuid, participantGuid,
                            InstanceStatusType.CREATED, false, Instant.now().toEpochMilli(), payload.getTriggerId());

            if (instanceDto == null || StringUtils.isBlank(instanceDto.getGuid())) {
                ApiError err = new ApiError(ErrorCodes.SERVER_ERROR, String.format(
                        "Unable to create activity instance for participant %s", participantGuid));
                LOG.error(err.getMessage());
                ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
            } else {
                handle.attach(DataExportDao.class).queueDataSync(userDto.getUserId(), studyId);
                LOG.info("Created on-demand activity instance {} for study guid {}, activity code {}, participant guid {}",
                        instanceDto.getGuid(), studyGuid, activityCode, participantGuid);
            }
        });

        return null;
    }
}
