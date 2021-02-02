package org.broadinstitute.ddp.event.dsmtask.api;

import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_PARTICIPANT_ID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_USER_ID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskConstants.LOG_PREFIX_DSM_TASK;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskConstants.LOG_PREFIX_DSM_TASK_ERROR;
import static org.slf4j.LoggerFactory.getLogger;


import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.util.GsonUtil;
import org.slf4j.Logger;

/**
 * Receive and process Dsm task messages.
 */
public class DsmTaskReceiver implements MessageReceiver {

    private static final Logger LOG = getLogger(DsmTaskReceiver.class);

    private final ProjectSubscriptionName projectSubscriptionName;
    private final DsmTaskProcessorFactory dsmTaskProcessorFactory;
    private final DsmTaskResultSender dsmTaskResultSender;

    private final Gson gson = GsonUtil.standardGson();

    public DsmTaskReceiver(ProjectSubscriptionName projectSubscriptionName,
                           DsmTaskProcessorFactory dsmTaskProcessorFactory,
                           DsmTaskResultSender dsmTaskResultSender) {
        this.projectSubscriptionName = projectSubscriptionName;
        this.dsmTaskProcessorFactory = dsmTaskProcessorFactory;
        this.dsmTaskResultSender = dsmTaskResultSender;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
        DsmTaskData dsmTaskData = parseMessage(message, consumer);
        if (dsmTaskData != null) {
            LOG.info(LOG_PREFIX_DSM_TASK + "processing started: taskType={}, participantId={}, userId={}, data={}",
                    dsmTaskData.getTaskType(), dsmTaskData.getParticipantId(), dsmTaskData.getUserId(), dsmTaskData.getPayload());
            DsmTaskResultData dsmTaskResultData = dsmTaskProcessorFactory.getDsmTaskDescriptors(dsmTaskData.getTaskType())
                    .getDsmTaskProcessor().processDsmTask(dsmTaskData);
            sendResponse(dsmTaskResultData);
            consumer.ack();
        }
    }

    private DsmTaskData parseMessage(PubsubMessage message, AckReplyConsumer consumer) {
        String messageId = message.getMessageId();
        String taskType = message.getAttributesOrDefault(ATTR_TASK_TYPE, null);
        String participantId = message.getAttributesOrDefault(ATTR_PARTICIPANT_ID, null);
        String userId = message.getAttributesOrDefault(ATTR_USER_ID, null);
        String studyGuid = message.getAttributesOrDefault(ATTR_STUDY_GUID, null);
        String data = message.getData() != null ? message.getData().toStringUtf8() : null;

        LOG.info(LOG_PREFIX_DSM_TASK
                + "pubsub message received[subscription={}, id={}]: taskType={}, participantId={}, userId={}, studyGuid={}, data={}",
                projectSubscriptionName, messageId, taskType, participantId, userId, studyGuid, data);

        DsmTaskProcessorFactory.DsmTaskDescriptor dsmTaskDescriptor = dsmTaskProcessorFactory.getDsmTaskDescriptors(taskType);
        if (dsmTaskDescriptor == null) {
            LOG.error(LOG_PREFIX_DSM_TASK_ERROR + "pubsub message [id={}] has unknown taskType={}", messageId, taskType);
            consumer.ack();
            return null;
        }
        if (participantId == null || userId == null) {
            LOG.error(LOG_PREFIX_DSM_TASK_ERROR + "Some pubsub message [id={},taskType={}] attributes not specified:"
                    + " participantId={}, userId={}", messageId, taskType, participantId, userId);
            consumer.ack();
            return null;
        }

        Class<?> payloadClass = dsmTaskDescriptor.getPayloadClass();
        if (payloadClass != null) {
            var payloadObject = gson.fromJson(data, payloadClass);
            if (payloadObject == null) {
                LOG.error(LOG_PREFIX_DSM_TASK_ERROR + "DsmTask pubsub message [id={},taskType={}] payload should not be empty",
                        taskType, messageId);
                consumer.ack();
                return null;
            }
            return new DsmTaskData(messageId, taskType, participantId, userId, studyGuid, data, payloadObject);
        }
        return new DsmTaskData(messageId, taskType, participantId, userId, studyGuid);
    }

    private void sendResponse(DsmTaskResultData dsmTaskResultData) {
        dsmTaskResultSender.sendDsmTaskResult(dsmTaskResultData);
    }
}
