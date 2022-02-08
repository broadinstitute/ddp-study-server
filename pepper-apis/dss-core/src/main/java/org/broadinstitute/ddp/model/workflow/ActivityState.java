package org.broadinstitute.ddp.model.workflow;

import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;

public class ActivityState implements WorkflowState {

    private long activityId;
    private boolean checkEachInstance;
    private ActivityInstanceDto candidateInstance;

    public ActivityState(long activityId) {
        this.activityId = activityId;
    }

    public ActivityState(long activityId, boolean checkEachInstance) {
        this.activityId = activityId;
        this.checkEachInstance = checkEachInstance;
    }

    @Override
    public StateType getType() {
        return StateType.ACTIVITY;
    }

    @Override
    public boolean matches(WorkflowState other) {
        return other != null && other.getType() == StateType.ACTIVITY
                && ((ActivityState) other).getActivityId() == activityId
                && ((ActivityState) other).shouldCheckEachInstance() == checkEachInstance;
    }

    @Override
    public String toString() {
        return "ActivityState{activityId=" + activityId + ",checkEachInstance=" + checkEachInstance + "}";
    }

    public long getActivityId() {
        return activityId;
    }

    public boolean shouldCheckEachInstance() {
        return checkEachInstance;
    }

    public ActivityInstanceDto getCandidateInstance() {
        return candidateInstance;
    }

    public void setCandidateInstance(ActivityInstanceDto candidateInstance) {
        this.candidateInstance = candidateInstance;
    }
}
