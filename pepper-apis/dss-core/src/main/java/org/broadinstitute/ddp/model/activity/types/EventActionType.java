package org.broadinstitute.ddp.model.activity.types;

public enum EventActionType {
    ACTIVITY_INSTANCE_CREATION(false),
    ANNOUNCEMENT(false),
    COPY_ANSWER(false),
    CREATE_INVITATION(false),
    HIDE_ACTIVITIES(false),
    MARK_ACTIVITIES_READ_ONLY(false),
    NOTIFICATION(false),
    PDF_GENERATION(false),
    REVOKE_PROXIES(true),
    UPDATE_CUSTOM_WORKFLOW(false),
    UPDATE_USER_STATUS(false),
    USER_ENROLLED(true),
    CREATE_KIT(false);


    private boolean isStatic;

    EventActionType(boolean isStatic) {
        this.isStatic = isStatic;
    }

    /**
     * Whether action type is "static" or not. If false, action requires attributes other than the type.
     *
     * @return static or not
     */
    public boolean isStatic() {
        return isStatic;
    }
}
