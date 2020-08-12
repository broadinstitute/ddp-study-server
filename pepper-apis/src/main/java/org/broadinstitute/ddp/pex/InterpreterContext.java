package org.broadinstitute.ddp.pex;

import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.jdbi.v3.core.Handle;

/**
 * Represents context information the interpreter needs in order to properly evaluate expressions.
 * This is package-private and only used within the interpreter. And this is immutable since we
 * don't want the context to change in the middle of evaluation.
 */
class InterpreterContext {

    private final Handle handle;
    private final String userGuid;
    private final String activityInstanceGuid;
    private UserActivityInstanceSummary activityInstanceSummary;

    // enum for whether ai guid is relevant

    InterpreterContext(Handle handle, String userGuid, String activityInstanceGuid) {
        this.handle = handle;
        this.userGuid = userGuid;
        this.activityInstanceGuid = activityInstanceGuid;
    }

    InterpreterContext(Handle handle, String userGuid, String activityInstanceGuid,
                       UserActivityInstanceSummary activityInstanceSummary) {
        this(handle, userGuid, activityInstanceGuid);
        this.activityInstanceSummary = activityInstanceSummary;
    }

    public Handle getHandle() {
        return handle;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getActivityInstanceGuid() {
        return activityInstanceGuid;
    }

    public UserActivityInstanceSummary getActivityInstanceSummary() {
        return activityInstanceSummary;
    }
}
