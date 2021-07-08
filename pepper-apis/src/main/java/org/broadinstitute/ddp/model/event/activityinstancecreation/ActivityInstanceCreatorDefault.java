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
public class ActivityInstanceCreatorDefault {

    protected final long studyActivityId;
    protected final ActivityInstanceCreationService creationService;

    public ActivityInstanceCreatorDefault(long studyActivityId, ActivityInstanceCreationService creationService) {
        this.studyActivityId = studyActivityId;
        this.creationService = creationService;
    }

    public void create(Handle handle, EventSignal signal) {
        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        JdbiActivityInstanceStatus jdbiActivityInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);

        ActivityDto activityDto = jdbiActivity.queryActivityById(studyActivityId);
        creationService.checkSignalIfNestedTargetActivity(activityDto);

        Integer numberOfActivitiesLeft = creationService.detectNumberOfActivitiesLeft(studyActivityId, activityDto, jdbiActivityInstance);

        if (numberOfActivitiesLeft == null || numberOfActivitiesLeft > 0) {

            creationService.hideExistingInstancesIfRequired(studyActivityId, handle, activityDto);

            Long parentInstanceId = creationService.detectParentInstanceId(activityDto);

            createActivityInstance(handle, signal, jdbiActivityInstance, jdbiActivityInstanceStatus, parentInstanceId);
        }
    }

    /**
     * Create an activity instance.
     *
     * @param parentInstanceId  ID of activity instance which is a current one (parent)
     * @return long  ID of a new instance
     */
    protected long createActivityInstance(
            Handle handle,
            EventSignal signal,
            JdbiActivityInstance jdbiActivityInstance,
            JdbiActivityInstanceStatus jdbiActivityInstanceStatus,
            Long parentInstanceId) {
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

        createChildActivityInstances(
                studyActivityId,
                newActivityInstanceId,
                newActivityInstanceGuid,
                jdbiActivityInstance,
                jdbiActivityInstanceStatus,
                handle);

        return newActivityInstanceId;
    }

    /**
     * Create child nested activity instances, if any
     */
    public void createChildActivityInstances(
            long parentActivityId,
            long parentActivityInstanceId,
            String parentActivityInstanceGuid,
            JdbiActivityInstance jdbiActivityInstance,
            JdbiActivityInstanceStatus jdbiActivityInstanceStatus,
            Handle handle) {

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
}
