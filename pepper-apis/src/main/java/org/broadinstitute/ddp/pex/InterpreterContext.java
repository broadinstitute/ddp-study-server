package org.broadinstitute.ddp.pex;

import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.model.event.EventSignal;
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
    private final UserActivityInstanceSummary activityInstanceSummary;
    private final EventSignal eventSignal;

    InterpreterContext(Handle handle, String userGuid, String activityInstanceGuid,
                       UserActivityInstanceSummary activityInstanceSummary, EventSignal eventSignal) {
        this.handle = handle;
        this.userGuid = userGuid;
        this.activityInstanceGuid = activityInstanceGuid;
        this.activityInstanceSummary = activityInstanceSummary;
        this.eventSignal = eventSignal;
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

    public EventSignal getEventSignal() {
        return eventSignal;
    }
}
