package org.broadinstitute.ddp.event.publish.pubsub;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.publish.pubsub.PubSubUtil.publishMessage;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_NAME__TASK_TYPE;

import java.util.Map;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.util.ConfigManager;

/**
 * Implementation of {@link TaskPublisher} providing publishing
 * an task (for DSM) of a specified type to Google PubSub topic.<br>
 * The parameters for PubSub publishing are taken from config:
 * `pubsub.pubSubDsmTaskTopic` - name of PubSub topic where to publish a task.
 */
@Slf4j
public class TaskPubSubPublisher implements TaskPublisher {
    public static final String TASK_PARTICIPANT_REGISTERED = "PARTICIPANT_REGISTERED";

    @Override
    public void publishTask(String taskType, String payload, String studyGuid, String participantGuid) {
        var pubSubMessage = createPubSubMessage(taskType, payload, studyGuid, participantGuid);
        var publisher = createPublisher(taskType);
        if (publisher != null) {
            var logMessage = format("task '%s' to pubsub topic='%s': studyGuid=%s, participantGuid=%s, payload={%s}",
                    taskType, publisher.getTopicNameString(), studyGuid, participantGuid, payload);
            publishMessage(publisher, pubSubMessage, logMessage);
        }
    }

    private PubsubMessage createPubSubMessage(String taskType, String eventPayload, String studyGuid, String participantGuid) {
        var messageBuilder = PubsubMessage.newBuilder();
        messageBuilder.setData(ByteString.copyFromUtf8(eventPayload));
        messageBuilder.putAllAttributes(Map.of(
                ATTR_NAME__TASK_TYPE, taskType,
                ATTR_NAME__STUDY_GUID, studyGuid,
                ATTR_NAME__PARTICIPANT_GUID, participantGuid
        ));
        return messageBuilder.build();
    }

    private Publisher createPublisher(String taskType) {
        try {
            return PubSubPublisherInitializer.getOrCreatePublisher(
                    ConfigManager.getInstance().getConfig().getString(ConfigFile.PUBSUB_DSM_TASKS_TOPIC));
        } catch (Exception e) {
            log.error("Error during publishing a task {} to PubSub topic", taskType, e);
        }
        return null;
    }
}
