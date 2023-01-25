package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException.Severity.WARN;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.ATTR_NAME__USER_ID;

import static org.apache.commons.lang3.StringUtils.isBlank;
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
        updateData(pubSubTask);
    }

    protected void validateTaskData(PubSubTask pubSubTask) {
        super.validateTaskData(pubSubTask);
        String userId = pubSubTask.getAttributes().get(ATTR_NAME__USER_ID);
        if (isBlank(participantGuid) || isBlank(userId)) {
            throw new PubSubTaskException(format("PubSubTask '%s' processing FAILED, some attributes are not specified: "
                            + "participantGuid=%s, userId=%s", pubSubTask.getTaskType(), participantGuid, userId), WARN);
        }
    }

    protected void updateData(PubSubTask pubSubTask) {
        var participantGuid = pubSubTask.getAttributes().get(ATTR_NAME__PARTICIPANT_GUID);

        new UpdateEmailHandler().updateEmail(participantGuid, payloadProps);

        new UpdateProfileDataHandler().updateProfileData(participantGuid, payloadProps);
    }
}
