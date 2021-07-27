package org.broadinstitute.ddp.model.event.activityinstancecreation;

import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.jdbi.v3.core.Handle;

/**
 * Default activity instance creator (creates one activity instance).
 * This implementation is used when event parameter `createFromAnswer` = false.
 */
public class ActivityInstanceCreationEventSyncProcessorDefault extends ActivityInstanceCreationEventSyncProcessor {

    /**
     * In default case created only 1 activity instance
     */
    private static final int INSTANCES_TO_CREATE_COUNT = 1;

    public ActivityInstanceCreationEventSyncProcessorDefault(Handle handle, EventSignal signal, long studyActivityId,
                                                             ActivityInstanceCreationService creationService) {
        super(handle, signal, studyActivityId, creationService);
    }

    @Override
    public void processInstancesCreation() {
        activityDto = jdbiActivity.queryActivityById(studyActivityId);
        creationService.checkSignalIfNestedTargetActivity(activityDto.getParentActivityId());

        if (detectPossibleNumberOfInstancesToCreate(INSTANCES_TO_CREATE_COUNT) >= INSTANCES_TO_CREATE_COUNT) {
            creationService.hideExistingInstancesIfRequired(studyActivityId, handle, activityDto);
            createActivityInstance(activityDto.getParentActivityId());
        }
    }

    /**
     * Get possible number of instances to create.
     */
    @Override
    public int detectPossibleNumberOfInstancesToCreate(int instancesToCreate) {
        var numberOfActivitiesLeft = creationService.detectNumberOfActivitiesLeft(studyActivityId, activityDto, jdbiActivityInstance);
        return numberOfActivitiesLeft == null || numberOfActivitiesLeft > 0 ? INSTANCES_TO_CREATE_COUNT : 0;
    }
}
