package org.broadinstitute.ddp.model.event.activityinstancecreation;

import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.DsmNotificationSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.jdbi.v3.core.Handle;


/**
 * Base class implementing a synchronous processor for event of type {@link EventActionType#ACTIVITY_INSTANCE_CREATION}
 */
public abstract class ActivityInstanceCreationEventSyncProcessor {

    protected final Handle handle;
    protected final EventSignal signal;

    protected final JdbiActivityInstance jdbiActivityInstance;
    protected final JdbiActivityInstanceStatus jdbiActivityInstanceStatus;
    protected final JdbiActivity jdbiActivity;

    protected final long studyActivityId;
    protected final ActivityInstanceCreationService creationService;

    protected ActivityDto activityDto;


    public ActivityInstanceCreationEventSyncProcessor(
            Handle handle, EventSignal signal, long studyActivityId, ActivityInstanceCreationService creationService) {
        this.handle = handle;
        this.signal = signal;
        this.studyActivityId = studyActivityId;
        this.creationService = creationService;
        this.jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        this.jdbiActivityInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);
        this.jdbiActivity = handle.attach(JdbiActivity.class);
    }

    /**
     * Create activity instance (or instances).
     * Do necessary pre- and post-processing (depending on implementation).
     */
    public abstract void processInstancesCreation();

    /**
     * Detect number of instances which can be really created
     */
    protected abstract int detectPossibleNumberOfInstancesToCreate(int instancesToCreate);

    /**
     * Create an activity instance.
     *
     * @return long  ID of a new instance
     */
    public long createActivityInstance(Long parentActivityId) {

        var parentInstanceId = creationService.detectParentInstanceId(parentActivityId);

        var creationResult = creationService.createActivityInstance(
                studyActivityId,
                parentInstanceId,
                jdbiActivityInstance,
                jdbiActivityInstanceStatus,
                false,
                null);

        var newActivityInstanceId = creationResult.getActivityInstanceId();
        var newActivityInstanceGuid = creationResult.getActivityInstanceGuid();

        if (signal instanceof DsmNotificationSignal) {
            creationService.saveKitEventData(handle, newActivityInstanceId);
        }

        ActivityInstanceUtil.populateDefaultValues(handle, newActivityInstanceId, signal.getOperatorId());

        creationService.runDownstreamEvents(studyActivityId, newActivityInstanceId, handle);

        createChildActivityInstances(studyActivityId, newActivityInstanceId, newActivityInstanceGuid);

        return newActivityInstanceId;
    }

    /**
     * Create child nested activity instances, if any
     */
    public void createChildActivityInstances(long parentActivityId, long parentActivityInstanceId, String parentActivityInstanceGuid) {
        var childActIdsToCreate = handle.attach(JdbiActivity.class).findChildActivityIdsThatNeedCreation(parentActivityId);
        for (var activityId : childActIdsToCreate) {
            ActivityInstanceCreationService.ActivityInstanceCreationResult creationResult = creationService.createActivityInstance(
                    activityId,
                    parentActivityInstanceId,
                    jdbiActivityInstance,
                    jdbiActivityInstanceStatus,
                    true,
                    parentActivityInstanceGuid);
            ActivityInstanceUtil.populateDefaultValues(handle, creationResult.getActivityInstanceId(), signal.getOperatorId());
            creationService.runDownstreamEvents(activityId, creationResult.getActivityInstanceId(), handle);
        }
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
