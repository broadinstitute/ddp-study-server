package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import java.util.Map;

import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;
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

    public static final String TASK_TYPE__UPDATE_PROFILE = "UPDATE_PROFILE";


    @Override
    public void handleTask(PubSubTask pubSubTask) {
        Map<String, String> payload = gson.fromJson(pubSubTask.getPayloadJson(), Map.class);

        new UpdateEmailHandler().updateEmail(pubSubTask.getParticipantGuid(), payload);
        new UpdateFirstLastNameHandler().updateFirstLastName(pubSubTask.getParticipantGuid(), payload);
    }
}
