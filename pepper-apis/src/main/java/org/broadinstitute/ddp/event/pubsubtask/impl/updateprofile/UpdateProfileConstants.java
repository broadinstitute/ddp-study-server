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

    // attribute names
    public static final String ATTR_PARTICIPANT_GUID = "participantGuid";
    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_STUDY_GUID = "studyGuid";

    // possible payload fields
    static final String FIELD_EMAIL = "email";
    static final String FIELD_FIRST_NAME = "firstName";
    static final String FIELD_LAST_NAME = "lastName";
}
