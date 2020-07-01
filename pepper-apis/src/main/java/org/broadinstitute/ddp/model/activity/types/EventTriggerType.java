package org.broadinstitute.ddp.model.activity.types;

public enum EventTriggerType {
    ACTIVITY_STATUS(false),
    CONSENT_SUSPENDED(true),
    DSM_NOTIFICATION(false),
    EXIT_REQUEST(true),
    GOVERNED_USER_REGISTERED(true),
    INVITATION_CREATED(true),
    JOIN_MAILING_LIST(true),
    LOGIN_ACCOUNT_CREATED(true),
    MEDICAL_UPDATE(true),
    REACHED_AOM(true),
    REACHED_AOM_PREP(true),
    USER_NOT_IN_STUDY(true),
    USER_REGISTERED(true),
    WORKFLOW_STATE(false);

    private boolean isStatic;

    EventTriggerType(boolean isStatic) {
        this.isStatic = isStatic;
    }

    /**
     * Whether trigger type is "static" or not. If false, trigger requires attributes other than the type.
     *
     * @return static or not
     */
    public boolean isStatic() {
        return isStatic;
    }
}
