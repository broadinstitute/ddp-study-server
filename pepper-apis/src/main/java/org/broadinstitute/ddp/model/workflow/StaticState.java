package org.broadinstitute.ddp.model.workflow;

public class StaticState implements WorkflowState {

    private StateType type;

    public static StaticState start() {
        return new StaticState(StateType.START);
    }

    public static StaticState done() {
        return new StaticState(StateType.DONE);
    }

    public static StaticState dashboard() {
        return new StaticState(StateType.DASHBOARD);
    }

    public static StaticState internationalPatients() {
        return new StaticState(StateType.INTERNATIONAL_PATIENTS);
    }

    public static StaticState thankYou() {
        return new StaticState(StateType.THANK_YOU);
    }

    public static StaticState mailingList() {
        return new StaticState(StateType.MAILING_LIST);
    }

    public static StaticState of(StateType type) {
        if (type.isStatic()) {
            return new StaticState(type);
        } else {
            return null;
        }
    }

    public static StaticState returningUser() {
        return new StaticState(StateType.RETURN_USER);
    }

    private StaticState(StateType type) {
        this.type = type;
    }

    @Override
    public StateType getType() {
        return type;
    }

    @Override
    public boolean matches(WorkflowState other) {
        return other != null && other.getType() == type;
    }

    @Override
    public String toString() {
        return "StaticState{type=" + type + "}";
    }
}
