package org.broadinstitute.ddp.model.event;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.dsm.TestResult;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.EventService;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityInstanceCreationEventAction extends EventAction {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityInstanceCreationEventAction.class);

    private long studyActivityId;

    public ActivityInstanceCreationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        studyActivityId = dto.getActivityInstanceCreationStudyActivityId();
    }

    public ActivityInstanceCreationEventAction(EventConfiguration eventConfiguration, long studyActivityId) {
        super(eventConfiguration, null);
        this.studyActivityId = studyActivityId;
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        Integer delayBeforePosting = eventConfiguration.getPostDelaySeconds();
        if (delayBeforePosting != null && delayBeforePosting > 0) {
            long postAfter = Instant.now().getEpochSecond() + delayBeforePosting;
            if (!eventConfiguration.dispatchToHousekeeping()) {
                throw new DDPException("ActivityInstance creation with delaySeconds > 0 are currently only supported "
                        + "as asynchronous events. Please set dispatch_to_housekeeping to true");
            } else {
                //insert queued event
                QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
                long queuedEventId = queuedEventDao.insertActivityInstanceCreation(eventConfiguration.getEventConfigurationId(),
                        postAfter,
                        signal.getParticipantId(),
                        signal.getOperatorId()
                );
                LOG.info("Created activity instance queued event: {}", queuedEventId);
            }
        } else {
            //synchronous action
            doAction(handle, signal);
        }
    }

    public void doAction(Handle handle, EventSignal signal) {
        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        JdbiActivityInstanceStatus jdbiActivityInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);

        ActivityDto activityDto = jdbiActivity.queryActivityById(studyActivityId);

        // Checking if the maximum number of activities of this type is hit
        LOG.info("Checking if the maximum number of activities (n = {}) for the study activity (id = {}) is hit",
                activityDto.getMaxInstancesPerUser(), studyActivityId);

        int numExistingActivities = jdbiActivityInstance.getNumActivitiesForParticipant(
                studyActivityId,
                signal.getParticipantId());
        LOG.info("Found {} existing activity instances for study activity {}", numExistingActivities, studyActivityId);

        if (activityDto.getMaxInstancesPerUser() != null && numExistingActivities >= activityDto.getMaxInstancesPerUser()) {
            LOG.info(
                    "The number of existing study activities (n = {}) with id = {} is greater than or equal than"
                            + " the allowed maximum for this study activity (n = {}), skipping the configuration",
                    numExistingActivities,
                    studyActivityId,
                    activityDto.getMaxInstancesPerUser()
            );
            return;
        }

        if (activityDto.isHideExistingInstancesOnCreation()) {
            //hide existing instances
            handle.attach(ActivityInstanceDao.class).bulkUpdateIsHiddenByActivityIds(signal.getParticipantId(),
                    true, Set.of(studyActivityId));
        }

        // All fine, creating an activity instance
        String activityInstanceGuid = jdbiActivityInstance.generateUniqueGuid();
        long newActivityInstanceId = jdbiActivityInstance.insert(
                studyActivityId,
                signal.getParticipantId(),
                activityInstanceGuid,
                null,
                Instant.now().toEpochMilli(),
                null
        );
        // Using the low-level facility to avoid infinite recursion
        jdbiActivityInstanceStatus.insert(
                newActivityInstanceId,
                InstanceStatusType.CREATED,
                Instant.now().toEpochMilli(),
                signal.getParticipantId()
        );

        LOG.info("Performed the instantiation of the study activity with the id {} triggered by"
                        + " {} Operator = {}, participant = {}, study = {}, created activity instance id = {}",
                studyActivityId, signal.toString(),
                signal.getOperatorId(),
                signal.getParticipantId(),
                signal.getStudyId(),
                newActivityInstanceId);

        if (signal instanceof DsmNotificationSignal) {
            var dsmSignal = (DsmNotificationSignal) signal;
            var builder = new RenderValueProvider.Builder();
            if (StringUtils.isNotBlank(dsmSignal.getKitRequestId())) {
                builder.setKitRequestId(dsmSignal.getKitRequestId());
            }
            if (dsmSignal.getDsmEventType() == DsmNotificationEventType.TEST_RESULT) {
                TestResult result = dsmSignal.getTestResult();
                if (result != null) {
                    builder.setTestResultCode(result.getNormalizedResult())
                            .setTestResultTimeCompleted(result.getTimeCompleted());
                }
            }
            Map<String, String> snapshot = builder.build().getSnapshot();
            if (!snapshot.isEmpty()) {
                handle.attach(ActivityInstanceDao.class).saveSubstitutions(newActivityInstanceId, snapshot);
                LOG.info("Saved kit event data as substitution snapshot for activity instance {}", newActivityInstanceId);
            }
        }

        EventService.getInstance().processAllActionsForEventSignal(handle, new ActivityInstanceStatusChangeSignal(
                signal.getOperatorId(),
                signal.getParticipantId(),
                signal.getParticipantGuid(),
                signal.getOperatorGuid(),
                newActivityInstanceId,
                studyActivityId,
                signal.getStudyId(),
                InstanceStatusType.CREATED));
    }

    public long getStudyActivityId() {
        return studyActivityId;
    }
}
