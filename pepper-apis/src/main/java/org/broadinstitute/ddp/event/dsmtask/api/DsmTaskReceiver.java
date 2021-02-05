package org.broadinstitute.ddp.event.dsmtask.api;

import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskData.ATTR_USER_ID;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.infoMsg;
import static org.slf4j.LoggerFactory.getLogger;


import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.util.GsonUtil;
import org.slf4j.Logger;

/**
 * Receive and process DsmTask messages which are published by DSM to
 * topic "dsm-to-dss-tasks" and fetched by subscriber registered by
 * {@link DsmTaskPubSubConnectionCreator}.
 *
 * <p>When DsmTask is received it is processed by a {@link DsmTaskProcessor}
 * which detected by a message attribute 'taskType' (from
 * factory {@link DsmTaskProcessorFactory}.
 *
 * <p>After task is processed the result is sent back to DSM by
 * publishing result message to topic "dss-to-dsm-results".
 * The result message sending delegated to {@link DsmTaskResultSender}
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
        var dsmTaskData = parseMessage(message, consumer);
        if (dsmTaskData != null) {
            var dsmTaskResultData = processDsmTask(dsmTaskData);
            if (dsmTaskResultData.isNeedsToRetry()) {
                consumer.nack();
            } else {
                consumer.ack();
                sendResponse(dsmTaskResultData);
            }
        }
    }

    private DsmTaskResultData processDsmTask(DsmTaskData dsmTaskData) {
        var dsmTaskResultData = dsmTaskProcessorFactory.getDsmTaskDescriptors(dsmTaskData.getTaskType())
                .getDsmTaskProcessor().processDsmTask(dsmTaskData);
        return dsmTaskResultData;
    }

    private DsmTaskData parseMessage(PubsubMessage message, AckReplyConsumer consumer) {
        String messageId = message.getMessageId();
        String taskType = message.getAttributesOrDefault(ATTR_TASK_TYPE, null);
        String participantGuid = message.getAttributesOrDefault(ATTR_PARTICIPANT_GUID, null);
        String userId = message.getAttributesOrDefault(ATTR_USER_ID, null);
        String studyGuid = message.getAttributesOrDefault(ATTR_STUDY_GUID, null);
        String data = message.getData() != null ? message.getData().toStringUtf8() : null;

        LOG.info(infoMsg("Pubsub message received[subscription={}, id={}]: taskType={}, participantGuid={}, userId={}, "
                        + "studyGuid={}, data={}"),
                projectSubscriptionName, messageId, taskType, participantGuid, userId, studyGuid, data);

        DsmTaskProcessorFactory.DsmTaskDescriptor dsmTaskDescriptor = dsmTaskProcessorFactory.getDsmTaskDescriptors(taskType);
        if (dsmTaskDescriptor == null) {
            LOG.error(errorMsg("Pubsub message [id={}] has unknown taskType={}"), messageId, taskType);
            consumer.ack();
            return null;
        }
        if (participantGuid == null || userId == null) {
            LOG.error(errorMsg("Some attributes are not specified in the pubsub message [id={},taskType={}]:"
                    + " participantGuid={}, userId={}"), messageId, taskType, participantGuid, userId);
            consumer.ack();
            return null;
        }

        Class<?> payloadClass = dsmTaskDescriptor.getPayloadClass();
        if (payloadClass != null) {
            var payloadObject = gson.fromJson(data, payloadClass);
            if (payloadObject == null) {
                LOG.error(errorMsg("Empty payload in the pubsub message [id={},taskType={}]"), taskType, messageId);
                consumer.ack();
                return null;
            }
            return new DsmTaskData(messageId, taskType, participantGuid, userId, studyGuid, data, payloadObject);
        }
        return new DsmTaskData(messageId, taskType, participantGuid, userId, studyGuid);
    }

    private void sendResponse(DsmTaskResultData dsmTaskResultData) {
        dsmTaskResultSender.sendDsmTaskResult(dsmTaskResultData);
    }
}
