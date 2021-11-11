package org.broadinstitute.ddp.event.pubsubtask.impl.userdelete;

import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;

/**
 * Constants specific for taskType='USER_DELETE'
 */
public class UserDeleteConstants {

    /**
     * {@link PubSubTask} taskType for a user deletion
     */
    public static final String TASK_TYPE__USER_DELETE = "USER_DELETE";

    // possible payload fields
    static final String FIELD__COMMENT = "comment";
}
