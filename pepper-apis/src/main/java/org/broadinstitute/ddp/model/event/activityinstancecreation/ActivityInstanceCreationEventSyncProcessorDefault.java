package org.broadinstitute.ddp.model.event.activityinstancecreation;

import java.util.List;

import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.model.event.ActivityInstanceCreationEventAction;
import org.broadinstitute.ddp.model.event.DsmNotificationSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.jdbi.v3.core.Handle;

/**
 * Default activity instance creator (creates one activity instance).
 * Called from {@link ActivityInstanceCreationEventAction}.
 */
public class ActivityInstanceCreationEventSyncProcessorDefault implements ActivityInstanceCreationEventSyncProcessor {

    /** In default case created only 1 activity instance */
    private static final int INSTANCES_TO_CREATE_COUNT = 1;

    protected final Handle handle;
    protected final EventSignal signal;

    protected final JdbiActivityInstance jdbiActivityInstance;
    protected final JdbiActivityInstanceStatus jdbiActivityInstanceStatus;
    protected final JdbiActivity jdbiActivity;

    protected final long studyActivityId;
    protected final ActivityInstanceCreationService creationService;

    protected ActivityDto activityDto;


    public ActivityInstanceCreationEventSyncProcessorDefault(
            Handle handle, EventSignal signal, long studyActivityId, ActivityInstanceCreationService creationService) {
        this.handle = handle;
        this.signal = signal;
        this.studyActivityId = studyActivityId;
        this.creationService = creationService;
        this.jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        this.jdbiActivityInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);
        this.jdbiActivity = handle.attach(JdbiActivity.class);
    }

    @Override
    public void create() {
        activityDto = jdbiActivity.queryActivityById(studyActivityId);
        creationService.checkSignalIfNestedTargetActivity(activityDto.getParentActivityId());

        if (detectPossibleNumberOfInstancesToCreate(INSTANCES_TO_CREATE_COUNT) >= INSTANCES_TO_CREATE_COUNT) {

            creationService.hideExistingInstancesIfRequired(studyActivityId, handle, activityDto);

            createActivityInstance(activityDto.getParentActivityId());
        }
    }

    /**
     * Create an activity instance.
     *
     * @return long  ID of a new instance
     */
    @Override
    public long createActivityInstance(Long parentActivityId) {

        Long parentInstanceId = creationService.detectParentInstanceId(parentActivityId);

        ActivityInstanceCreationService.ActivityInstanceCreationResult creationResult = creationService.createActivityInstance(
                studyActivityId,
                parentInstanceId,
                jdbiActivityInstance,
                jdbiActivityInstanceStatus,
                false,
                null);

        long newActivityInstanceId = creationResult.getActivityInstanceId();
        String newActivityInstanceGuid = creationResult.getActivityInstanceGuid();

        if (signal instanceof DsmNotificationSignal) {
            creationService.saveKitEventData(handle, newActivityInstanceId);
        }

        creationService.runDownstreamEvents(studyActivityId, newActivityInstanceId, handle);

        createChildActivityInstances(studyActivityId, newActivityInstanceId, newActivityInstanceGuid);

        return newActivityInstanceId;
    }

    /**
     * Create child nested activity instances, if any
     */
    public void createChildActivityInstances(long parentActivityId, long parentActivityInstanceId, String parentActivityInstanceGuid) {
        List<Long> childActIdsToCreate = handle.attach(JdbiActivity.class).findChildActivityIdsThatNeedCreation(parentActivityId);
        for (var activityId : childActIdsToCreate) {
            ActivityInstanceCreationService.ActivityInstanceCreationResult creationResult = creationService.createActivityInstance(
                    activityId,
                    parentActivityInstanceId,
                    jdbiActivityInstance,
                    jdbiActivityInstanceStatus,
                    true,
                    parentActivityInstanceGuid);
            creationService.runDownstreamEvents(activityId, creationResult.getActivityInstanceId(), handle);
        }
    }

    /**
     * Get possible number of instances to create
     */
    @Override
    public int detectPossibleNumberOfInstancesToCreate(int instancesToCreate) {
        Integer numberOfActivitiesLeft = creationService.detectNumberOfActivitiesLeft(studyActivityId, activityDto, jdbiActivityInstance);
        return numberOfActivitiesLeft == null || numberOfActivitiesLeft > 0 ? INSTANCES_TO_CREATE_COUNT : 0;
    }

    public Handle getHandle() {
        return handle;
    }

    public EventSignal getSignal() {
        return signal;
    }

    public long getStudyActivityId() {
        return studyActivityId;
    }

    public ActivityInstanceCreationService getCreationService() {
        return creationService;
    }
}
