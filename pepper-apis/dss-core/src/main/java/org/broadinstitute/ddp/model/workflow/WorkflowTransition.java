package org.broadinstitute.ddp.model.workflow;

public class WorkflowTransition {

    private Long id;
    private long studyId;
    private WorkflowState fromState;
    private WorkflowState nextState;
    private String precondition;
    private int order;

    public WorkflowTransition(Long id, long studyId, WorkflowState fromState, WorkflowState nextState, String precondition, int order) {
        this.id = id;
        this.studyId = studyId;
        this.fromState = fromState;
        this.nextState = nextState;
        this.precondition = precondition;
        this.order = order;
    }

    public WorkflowTransition(long studyId, WorkflowState fromState, WorkflowState nextState, String precondition, int order) {
        this.studyId = studyId;
        this.fromState = fromState;
        this.nextState = nextState;
        this.precondition = precondition;
        this.order = order;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getStudyId() {
        return studyId;
    }

    public WorkflowState getFromState() {
        return fromState;
    }

    public WorkflowState getNextState() {
        return nextState;
    }

    public String getPrecondition() {
        return precondition;
    }

    public int getOrder() {
        return order;
    }
}
