package org.broadinstitute.ddp.model.event;

import java.time.Instant;

import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
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
        this.studyActivityId = dto.getActivityInstanceCreationStudyActivityId();
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        JdbiActivityInstanceStatus jdbiActivityInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        JdbiEventConfigurationOccurrenceCounter jdbiEventConfigurationOccurrenceCounter =
                handle.attach(JdbiEventConfigurationOccurrenceCounter.class);

        ActivityDto activityDto = jdbiActivity.queryActivityById(studyActivityId);

        // Checking if the maximum number of activities of this type is hit
        LOG.info("Checking if the maximum number of activities (n = {}) for the study activity (id = {}) is hit",
                activityDto.getMaxInstancesPerUser(), studyActivityId);

        int numExistingActivities = jdbiActivityInstance.getNumActivitiesForParticipant(
                studyActivityId,
                eventSignal.getParticipantId());
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

        // All fine, creating an activity instance
        String activityInstanceGuid = jdbiActivityInstance.generateUniqueGuid();
        long newActivityInstanceId = jdbiActivityInstance.insert(
                studyActivityId,
                eventSignal.getParticipantId(),
                activityInstanceGuid,
                false,
                Instant.now().toEpochMilli(),
                null
        );
        // Using the low-level facility to avoid infinite recursion
        jdbiActivityInstanceStatus.insert(
                newActivityInstanceId,
                InstanceStatusType.CREATED,
                Instant.now().toEpochMilli(),
                eventSignal.getParticipantId()
        );

        // Incrementing the counter indicating that the event configuration has been executed
        jdbiEventConfigurationOccurrenceCounter.incNumOccurrences(
                eventConfiguration.getEventConfigurationId(),
                eventSignal.getParticipantId()
        );
        LOG.info("Performed the instantiation of the study activity with the id {} triggered by"
                        + " {} Operator = {}, participant = {}, study = {}, created activity instance id = {}",
                studyActivityId, eventSignal.toString(),
                eventSignal.getOperatorId(),
                eventSignal.getParticipantId(),
                eventSignal.getStudyId(),
                newActivityInstanceId);

        EventService.getInstance().processAllActionsForEventSignal(handle, new ActivityInstanceStatusChangeSignal(
                eventSignal.getOperatorId(),
                eventSignal.getParticipantId(),
                eventSignal.getParticipantGuid(),
                newActivityInstanceId,
                studyActivityId,
                eventSignal.getStudyId(),
                InstanceStatusType.CREATED));
    }

    public Long getStudyActivityId() {
        return studyActivityId;
    }
}
