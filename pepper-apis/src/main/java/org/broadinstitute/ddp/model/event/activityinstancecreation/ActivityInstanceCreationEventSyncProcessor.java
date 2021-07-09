package org.broadinstitute.ddp.model.event.activityinstancecreation;

import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.service.ActivityInstanceCreationService;
import org.jdbi.v3.core.Handle;


/**
 * Interface defining a synchronous processor for event of type {@link EventActionType#ACTIVITY_INSTANCE_CREATION}
 */
public interface ActivityInstanceCreationEventSyncProcessor {

    void create();

    long createActivityInstance(Long parentActivityId);

    int detectPossibleNumberOfInstancesToCreate(int instancesToCreate);

    Handle getHandle();

    EventSignal getSignal();

    long getStudyActivityId();

    ActivityInstanceCreationService getCreationService();
}
