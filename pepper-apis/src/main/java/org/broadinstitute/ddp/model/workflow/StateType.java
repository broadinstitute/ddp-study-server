package org.broadinstitute.ddp.model.workflow;

public enum StateType {
    /** Indicates this state deals with an activity. */
    ACTIVITY(false),

    /** Indicates the dashboard homepage as end of workflow. */
    DASHBOARD(true),

    /** Indicates the end of the workflow. */
    DONE(true),

    /** Indicates user is returning to the app */
    RETURN_USER(true),

    /** Indicates the start of the workflow. */
    START(true),

    /** Indicates the end of a workflow where a thank you message should be shown. */
    THANK_YOU(true),

    /** Indicates the end of a workflow where a we have bounced international patients. */
    INTERNATIONAL_PATIENTS(true),

    /** Indicates routing the user to sign up for the mailing list. */
    MAILING_LIST(true);

    private boolean isStatic;

    StateType(boolean isStatic) {
        this.isStatic = isStatic;
    }

    /**
     * Is this state a static workflow state (i.e. does the state refer to a non-changing entity)?
     * For example, "dashboard" refers to a study's dashboard, whereas the "activity" state can refer to any one activity.
     *
     * @return true if static state, false otherwise
     */
    public boolean isStatic() {
        return isStatic;
    }
}
