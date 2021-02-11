package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_USER_ID;

import java.util.Properties;


import org.apache.commons.lang.StringUtils;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
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
    public void handleTask(PubSubTask pubSubTask) {
        validateTaskData(pubSubTask);
        updateData(pubSubTask);
    }

    protected void validateTaskData(PubSubTask pubSubTask) {
        String participantGuid = pubSubTask.getAttributes().get(ATTR_PARTICIPANT_GUID);
        String userId = pubSubTask.getAttributes().get(ATTR_USER_ID);
        if (participantGuid == null || userId == null) {
            throw new PubSubTaskException(format("Error processing taskType=%s - some attributes are not specified: "
                            + "participantGuid=%s, userId=%s", pubSubTask.getTaskType(), participantGuid, userId), pubSubTask);
        }
        if (StringUtils.isBlank(pubSubTask.getPayloadJson())) {
            throw new PubSubTaskException("Error processing taskType=%s: empty payload", pubSubTask);
        }
    }

    protected void updateData(PubSubTask pubSubTask) {
        Properties properties = gson.fromJson(pubSubTask.getPayloadJson(), Properties.class);

        var userGuid = pubSubTask.getAttributes().get(ATTR_PARTICIPANT_GUID);
        new UpdateEmailHandler().updateEmail(userGuid, properties);
        new UpdateFirstLastNameHandler().updateFirstLastName(userGuid, properties);
    }
}
