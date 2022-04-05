package org.broadinstitute.ddp.service;


import java.time.Instant;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.content.RenderValueProvider;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.dsm.TestResult;
import org.broadinstitute.ddp.model.event.ActivityInstanceStatusChangeSignal;
import org.broadinstitute.ddp.model.event.DsmNotificationSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.jdbi.v3.core.Handle;

/**
 * Provides creation of an activity instance in the DB
 */
@Slf4j
@AllArgsConstructor
public class ActivityInstanceCreationService {
    private final EventSignal signal;

    /**
     * Checking if the maximum number of activities of this type is hit
     */
    public Integer detectNumberOfActivitiesLeft(long studyActivityId, ActivityDto activityDto,
                                                JdbiActivityInstance jdbiActivityInstance) {
        log.info("Checking if the maximum number of activities (n = {}) for the study activity (id = {}) is hit",
                activityDto.getMaxInstancesPerUser(), studyActivityId);

        int numExistingActivities = jdbiActivityInstance.getNumActivitiesForParticipant(
                studyActivityId,
                signal.getParticipantId());
        log.info("Found {} existing activity instances for study activity {}", numExistingActivities, studyActivityId);

        Integer numberOfActivitiesLeft = activityDto.getMaxInstancesPerUser() == null ? null :
                activityDto.getMaxInstancesPerUser() - numExistingActivities;
        if (numberOfActivitiesLeft != null && numberOfActivitiesLeft <= 0) {
            log.info(
                    "The number of existing study activities (n = {}) with id = {} is greater than or equal than"
                            + " the allowed maximum for this study activity (n = {}), skipping the configuration",
                    numExistingActivities,
                    studyActivityId,
                    activityDto.getMaxInstancesPerUser()
            );
        }
        return numberOfActivitiesLeft;
    }

    /**
     * If needs to be hidden then hide existing activities
     */
    public void hideExistingInstancesIfRequired(long studyActivityId, Handle handle, ActivityDto activityDto) {
        if (activityDto.hideExistingInstancesOnCreation()) {
            handle.attach(ActivityInstanceDao.class).bulkUpdateIsHiddenByActivityIds(signal.getParticipantId(),
                    true, Set.of(studyActivityId));
        }
    }

    public Long detectParentInstanceId(Long parentActivityId) {
        Long parentInstanceId = null;
        if (parentActivityId != null && signal.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
            parentInstanceId = ((ActivityInstanceStatusChangeSignal) signal).getActivityInstanceIdThatChanged();
        }
        return parentInstanceId;
    }

    /**
     * Create a new activity instance and insert to DB
     */
    public ActivityInstanceCreationResult createActivityInstance(
            long studyActivityId,
            Long parentInstanceId,
            JdbiActivityInstance jdbiActivityInstance,
            JdbiActivityInstanceStatus jdbiActivityInstanceStatus,
            boolean isNestedActivity,
            String parentActivityInstanceGuid) {
        String activityInstanceGuid = jdbiActivityInstance.generateUniqueGuid();
        long newActivityInstanceId = jdbiActivityInstance.insert(
                studyActivityId,
                signal.getParticipantId(),
                activityInstanceGuid,
                null,
                Instant.now().toEpochMilli(),
                null,
                parentInstanceId,
                null,
                null,
                null
        );
        // Using the low-level facility to avoid infinite recursion
        jdbiActivityInstanceStatus.insert(
                newActivityInstanceId,
                InstanceStatusType.CREATED,
                Instant.now().toEpochMilli(),
                signal.getParticipantId()
        );

        if (isNestedActivity) {
            log.info("Created child instance {} for parent instance {}", activityInstanceGuid, parentActivityInstanceGuid);
        } else {
            log.info("Performed the instantiation of the study activity with the id {} triggered by"
                            + " {} Operator = {}, participant = {}, study = {}, created activity instance id = {}",
                    studyActivityId, signal,
                    signal.getOperatorId(),
                    signal.getParticipantId(),
                    signal.getStudyId(),
                    newActivityInstanceId);
        }

        return new ActivityInstanceCreationResult(newActivityInstanceId, activityInstanceGuid);
    }

    public void saveKitEventData(Handle handle, long newActivityInstanceId) {
        var dsmSignal = (DsmNotificationSignal) signal;
        var builder = new RenderValueProvider.Builder();
        if (StringUtils.isNotBlank(dsmSignal.getKitRequestId())) {
            builder.setKitRequestId(dsmSignal.getKitRequestId());
        }
        if (dsmSignal.getKitReasonType() != null) {
            builder.setKitReasonType(dsmSignal.getKitReasonType());
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
            log.info("Saved kit event data as substitution snapshot for activity instance {}", newActivityInstanceId);
        }
    }

    public void runDownstreamEvents(long studyActivityId, long activityInstanceId, Handle handle) {
        EventService.getInstance().processAllActionsForEventSignal(handle, new ActivityInstanceStatusChangeSignal(
                signal.getOperatorId(),
                signal.getParticipantId(),
                signal.getParticipantGuid(),
                signal.getOperatorGuid(),
                activityInstanceId,
                studyActivityId,
                signal.getStudyId(),
                signal.getStudyGuid(),
                InstanceStatusType.CREATED));
    }

    public void checkSignalIfNestedTargetActivity(Long parentActivityId) {
        if (parentActivityId != null) {
            if (signal.getEventTriggerType() != EventTriggerType.ACTIVITY_STATUS) {
                throw new DDPException("ActivityInstance creation action is not paired with ACTIVITY_STATUS trigger");
            }
            ActivityInstanceStatusChangeSignal statusSignal = (ActivityInstanceStatusChangeSignal) signal;
            if (statusSignal.getActivityIdThatChanged() != parentActivityId) {
                throw new DDPException("ActivityInstance creation action parent activity does not match trigger");
            }
        }
    }

    public static class ActivityInstanceCreationResult {

        private long activityInstanceId;
        private String activityInstanceGuid;

        public ActivityInstanceCreationResult(long activityInstanceId, String activityInstanceGuid) {
            this.activityInstanceId = activityInstanceId;
            this.activityInstanceGuid = activityInstanceGuid;
        }

        public long getActivityInstanceId() {
            return activityInstanceId;
        }

        public String getActivityInstanceGuid() {
            return activityInstanceGuid;
        }
    }
}
