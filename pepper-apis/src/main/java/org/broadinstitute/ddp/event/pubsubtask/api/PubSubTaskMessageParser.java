package org.broadinstitute.ddp.event.pubsubtask.api;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_USER_ID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.slf4j.LoggerFactory.getLogger;


import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.util.GsonUtil;
import org.slf4j.Logger;

/**
 * Parses {@link PubSubTask} from subscription
 * defined by config param "pubsub.pubSubTasksSubscription".
 * The result {@link PubSubTaskMessageParseResult} contains
 * created {@link PubSubTask} with parsed message.
 * If error occured during parsing then {@link PubSubTaskMessageParseResult}
 * contains non-null error message (to be sent to result topic).
 */
public class PubSubTaskMessageParser {

    private static final Logger LOG = getLogger(PubSubTaskMessageParser.class);

    private final ProjectSubscriptionName projectSubscriptionName;
    private final PubSubTaskProcessorFactory pubSubTaskProcessorFactory;

    private final Gson gson = GsonUtil.standardGson();

    public PubSubTaskMessageParser(ProjectSubscriptionName projectSubscriptionName,
                                   PubSubTaskProcessorFactory pubSubTaskProcessorFactory) {
        this.projectSubscriptionName = projectSubscriptionName;
        this.pubSubTaskProcessorFactory = pubSubTaskProcessorFactory;
    }

    public PubSubTaskMessageParseResult parseMessage(PubsubMessage message) {
        String messageId = message.getMessageId();
        String taskType = message.getAttributesOrDefault(ATTR_TASK_TYPE, null);
        String participantGuid = message.getAttributesOrDefault(ATTR_PARTICIPANT_GUID, null);
        String userId = message.getAttributesOrDefault(ATTR_USER_ID, null);
        String studyGuid = message.getAttributesOrDefault(ATTR_STUDY_GUID, null);
        String payloadJson = message.getData() != null ? message.getData().toStringUtf8() : null;

        LOG.info(infoMsg("Pubsub message received[subscription={}, id={}]: taskType={}, participantGuid={}, userId={}, "
                        + "studyGuid={}, data={}"),
                projectSubscriptionName, messageId, taskType, participantGuid, userId, studyGuid, payloadJson);

        var pubSubTaskDescriptor = pubSubTaskProcessorFactory.getPubSubTaskDescriptors(taskType);

        String errorMessage = null;
        Object payloadObject = null;

        if (pubSubTaskDescriptor == null) {
            errorMessage = format(errorMsg("Pubsub message [id=%s] has unknown taskType=%s"), messageId, taskType);
        } else  if (participantGuid == null || userId == null) {
            errorMessage = format(errorMsg("Some attributes are not specified in the pubsub message [id=%s,taskType=%s]:"
                    + " participantGuid=%s, userId=%s"), messageId, taskType, participantGuid, userId);
        }

        if (errorMessage == null) {
            Class<?> payloadClass = pubSubTaskDescriptor.getPayloadClass();
            if (payloadClass != null) {
                payloadObject = gson.fromJson(payloadJson, payloadClass);
            }
        }

        if (StringUtils.isBlank(payloadJson)) {
            errorMessage = format(errorMsg("Empty payload in the pubsub message [id=%s,taskType=%s]"), messageId, taskType);
        }

        return new PubSubTaskMessageParseResult(
                new PubSubTask(messageId, taskType, participantGuid, userId, studyGuid, payloadJson, payloadObject),
                errorMessage);
    }


    /**
     * Holds a result of PubSubTask message parsing.
     * In case of error during parsing the error message set to {@link #errorMessage}
     */
    public static class PubSubTaskMessageParseResult {

        private final PubSubTask pubSubTask;
        private final String errorMessage;

        public PubSubTaskMessageParseResult(PubSubTask pubSubTask, String errorMessage) {
            this.pubSubTask = pubSubTask;
            this.errorMessage = errorMessage;
        }

        public PubSubTask getPubSubTask() {
            return pubSubTask;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

}
