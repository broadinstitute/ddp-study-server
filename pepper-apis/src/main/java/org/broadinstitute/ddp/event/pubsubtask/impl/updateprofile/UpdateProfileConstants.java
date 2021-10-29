package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;

/**
 * Constants specific for taskType='UPDATE_PROFILE'
 */
public class UpdateProfileConstants {

    /**
     * {@link PubSubTask} taskType for updating some user profile data
     */
    public static final String TASK_TYPE__UPDATE_PROFILE = "UPDATE_PROFILE";

    // task-specific attributes (in addition to the common ones)
    public static final String ATTR_NAME__USER_ID = "userId";

    // possible payload fields
    static final String FIELD__EMAIL = "email";
    static final String FIELD__FIRST_NAME = "firstName";
    static final String FIELD__LAST_NAME = "lastName";
    static final String FIELD__DO_NOT_CONTACT = "doNotContact";
}
