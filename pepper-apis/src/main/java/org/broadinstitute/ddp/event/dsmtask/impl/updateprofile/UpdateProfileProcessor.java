package org.broadinstitute.ddp.event.dsmtask.impl.updateprofile;

import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskResultData.DsmTaskResultType.ERROR;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskResultData.DsmTaskResultType.SUCCESS;


import org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData;
import org.broadinstitute.ddp.event.dsmtask.api.DsmTaskProcessor;
import org.broadinstitute.ddp.event.dsmtask.api.DsmTaskResultData;

/**
 * Processes Dsm Task of taskType='UPDATE_PROFILE'.
 * It updates person's email, first, and last name.
 *
 * <p>Does the following steps:
 * <ul>
 *     <li>updates user's email (checking if this email not used by another user);</li>
 *     <li>updates user's first and last name.</li>
 * </ul>
 * NOTE: if any data (passed from Dsm) is null then it not updated.
 */
public class UpdateProfileProcessor implements DsmTaskProcessor {

    public static final String TASK_TYPE__UPDATE_PROFILE = "UPDATE_PROFILE";

    @Override
    public DsmTaskResultData processDsmTask(DsmTaskData dsmTaskData) {
        UpdateProfileData updateProfileData = (UpdateProfileData)dsmTaskData.getPayloadObject();
        try {
            updateEmail(dsmTaskData.getParticipantGuid(), updateProfileData.getEmail());
            updateFirstLastName(dsmTaskData.getParticipantGuid(),
                    updateProfileData.getFirstName(), updateProfileData.getLastName());
        } catch (Exception e) {
            return new DsmTaskResultData(ERROR, e.getMessage(), dsmTaskData);
        }
        return new DsmTaskResultData(SUCCESS, dsmTaskData);
    }

    private void updateEmail(String userGuid, String email) {
        if (email != null) {
            new UpdateEmailHandler().doIt(userGuid, email);
        }
    }

    private void updateFirstLastName(String userGuid, String firstName, String lastName) {
        if (firstName != null || lastName != null) {
            new UpdateFirstLastNameHandler().doIt(userGuid, firstName, lastName);
        }
    }
}
