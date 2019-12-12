package org.broadinstitute.ddp.model.workflow;

public interface WorkflowState {

    /**
     * Get the state type represented by this state.
     *
     * @return the type
     */
    StateType getType();

    /**
     * Determine if two things represent the same state.
     *
     * @param other the workflow state to compare against
     * @return true if same, false otherwise
     */
    boolean matches(WorkflowState other);
}
