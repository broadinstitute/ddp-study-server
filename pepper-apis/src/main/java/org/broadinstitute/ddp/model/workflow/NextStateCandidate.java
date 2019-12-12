package org.broadinstitute.ddp.model.workflow;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;

/**
 * Represents a potential next state that we can transition to in workflow. The "precondition" PEX expression should be
 * evaluated to determine whether transition is actually allowed or not.
 */
public class NextStateCandidate {

    private long transitionId;
    private StateType nextStateType;
    private Long nextActivityId;
    private String precondition;

    public NextStateCandidate(long transitionId, StateType nextStateType, Long nextActivityId, String precondition) {
        this.transitionId = transitionId;
        this.nextStateType = nextStateType;
        this.nextActivityId = nextActivityId;
        this.precondition = precondition;
    }

    public long getTransitionId() {
        return transitionId;
    }

    public StateType getNextStateType() {
        return nextStateType;
    }

    public Long getNextActivityId() {
        return nextActivityId;
    }

    public String getPrecondition() {
        return precondition;
    }

    public boolean hasPrecondition() {
        return StringUtils.isNotBlank(precondition);
    }

    public WorkflowState asWorkflowState() {
        if (nextStateType.isStatic()) {
            return StaticState.of(nextStateType);
        } else if (nextStateType == StateType.ACTIVITY) {
            if (nextActivityId == null) {
                throw new DDPException("Workflow activity state is missing an activity id for transition id " + transitionId);
            }
            return new ActivityState(nextActivityId);
        } else {
            throw new DDPException("Unhandled workflow state type " + nextStateType);
        }
    }
}
