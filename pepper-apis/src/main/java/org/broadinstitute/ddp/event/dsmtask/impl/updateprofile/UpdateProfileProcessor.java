package org.broadinstitute.ddp.event.dsmtask.impl.updateprofile;

import org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData;
import org.broadinstitute.ddp.event.dsmtask.api.DsmTaskProcessorAbstract;

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
public class UpdateProfileProcessor extends DsmTaskProcessorAbstract {

    public static final String TASK_TYPE__UPDATE_PROFILE = "UPDATE_PROFILE";

    @Override
    public void handleTask(DsmTaskData dsmTaskData) {
        var updateProfileData = (UpdateProfileData)dsmTaskData.getPayloadObject();

        updateEmail(dsmTaskData.getParticipantGuid(), updateProfileData.getEmail());

        updateFirstLastName(dsmTaskData.getParticipantGuid(), updateProfileData.getFirstName(), updateProfileData.getLastName());
    }

    private void updateEmail(String userGuid, String email) {
        if (email != null) {
            new UpdateEmailHandler().updateEmail(userGuid, email);
        }
    }

    private void updateFirstLastName(String userGuid, String firstName, String lastName) {
        if (firstName != null || lastName != null) {
            new UpdateFirstLastNameHandler().updateFirstLastName(userGuid, firstName, lastName);
        }
    }
}
