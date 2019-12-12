package org.broadinstitute.ddp.model.workflow;

public class ActivityState implements WorkflowState {

    private long activityId;

    public ActivityState(long activityId) {
        this.activityId = activityId;
    }

    @Override
    public StateType getType() {
        return StateType.ACTIVITY;
    }

    @Override
    public boolean matches(WorkflowState other) {
        return other != null && other.getType() == StateType.ACTIVITY
                && ((ActivityState) other).getActivityId() == activityId;
    }

    @Override
    public String toString() {
        return "ActivityState{activityId=" + activityId + "}";
    }

    public long getActivityId() {
        return activityId;
    }
}
