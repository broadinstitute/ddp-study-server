package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_PARTICIPANT_GUID;


import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskDataReader;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskProcessorAbstract;


/**
 * Processes PubSubTask of taskType='UPDATE_PROFILE'.
 * It updates person's email, first, and last name.
 *
 * <p>Does the following steps:
 * <ul>
 *     <li>updates user's email (checking if this email not used by another user);</li>
 *     <li>updates user's first and last name.</li>
 * </ul>
 * NOTE: if any data field is null then it is not updated.
 */
public class UpdateProfileProcessor extends PubSubTaskProcessorAbstract {

    @Override
    public void handleTask(PubSubTask pubSubTask, PubSubTaskDataReader.PubSubTaskPayloadData payloadData) {
        var userGuid = pubSubTask.getAttributes().get(ATTR_PARTICIPANT_GUID);
        new UpdateEmailHandler().updateEmail(userGuid, payloadData.getProperties());
        new UpdateFirstLastNameHandler().updateFirstLastName(userGuid, payloadData.getProperties());
    }
}
