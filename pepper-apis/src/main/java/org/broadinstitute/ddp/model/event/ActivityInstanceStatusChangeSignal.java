package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

public class ActivityInstanceStatusChangeSignal extends EventSignal {

    private long activityInstanceIdThatChanged;
    private long activityIdThatChanged;
    private InstanceStatusType targetStatusType;

    public ActivityInstanceStatusChangeSignal(long operatorId,
                                              long participantId,
                                              String participantGuid,
                                              String operatorGuid,
                                              long activityInstanceIdThatChanged,
                                              long activityIdThatChanged,
                                              long studyId,
                                              InstanceStatusType targetStatusType) {
        super(operatorId, participantId, participantGuid, operatorGuid, studyId, EventTriggerType.ACTIVITY_STATUS);
        this.activityInstanceIdThatChanged = activityInstanceIdThatChanged;
        this.activityIdThatChanged = activityIdThatChanged;
        this.targetStatusType = targetStatusType;
    }

    public long getActivityInstanceIdThatChanged() {
        return activityInstanceIdThatChanged;
    }

    public InstanceStatusType getTargetStatusType() {
        return targetStatusType;
    }

    public long getActivityIdThatChanged() {
        return activityIdThatChanged;
    }

    @Override
    public String toString() {
        return "ActivityInstanceStatusChangeSignal{"
                + "activityInstanceIdThatChanged=" + activityInstanceIdThatChanged
                + ", activityIdThatChanged=" + activityIdThatChanged
                + ", targetStatusType=" + targetStatusType
                + '}';
    }
}
