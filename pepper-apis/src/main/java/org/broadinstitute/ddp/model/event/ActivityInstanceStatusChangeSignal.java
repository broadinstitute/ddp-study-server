package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

public class ActivityInstanceStatusChangeSignal extends EventSignal {

    private long activityInstanceIdThatChanged;
    private InstanceStatusType targetStatusType;

    public ActivityInstanceStatusChangeSignal(long operatorId,
                                              long participantId,
                                              String participantGuid,
                                              long activityInstanceIdThatChanged,
                                              long studyId,
                                              InstanceStatusType targetStatusType) {
        super(operatorId, participantId, participantGuid, studyId, EventTriggerType.ACTIVITY_STATUS);
        this.activityInstanceIdThatChanged = activityInstanceIdThatChanged;
        this.targetStatusType = targetStatusType;
    }

    public long getActivityInstanceIdThatChanged() {
        return activityInstanceIdThatChanged;
    }

    public InstanceStatusType getTargetStatusType() {
        return targetStatusType;
    }

    @Override
    public String toString() {
        return "ActivityInstanceStatusChangeSignal{"
                + "activityInstanceIdThatChanged=" + activityInstanceIdThatChanged
                + ", targetStatusType=" + targetStatusType + "}";
    }
}
