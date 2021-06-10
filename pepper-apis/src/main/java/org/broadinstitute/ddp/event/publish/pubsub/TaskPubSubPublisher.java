package org.broadinstitute.ddp.event.publish.pubsub;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.publish.pubsub.PubSubUtil.publishMessage;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;

import java.util.Map;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of {@link TaskPublisher} providing publishing
 * an task (for DSM) of a specified type to Google PubSub topic.<br>
 * The parameters for PubSub publishing are taken from config:
 * `pubsub.pubSubDsmTaskTopic` - name of PubSub topic where to publish a task.
 */
public class TaskPubSubPublisher implements TaskPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(TaskPubSubPublisher.class);

    public static final String ATTR_STUDY_GUID = "studyGuid";
    public static final String ATTR_PARTICIPANT_GUID = "participantGuid";

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
                ATTR_TASK_TYPE, taskType,
                ATTR_STUDY_GUID, studyGuid,
                ATTR_PARTICIPANT_GUID, participantGuid
        ));
        return messageBuilder.build();
    }

    private Publisher createPublisher(String taskType) {
        try {
            return PubSubPublisherInitializer.getOrCreatePublisher(
                    ConfigManager.getInstance().getConfig().getString(ConfigFile.PUBSUB_DSM_TASKS_TOPIC));
        } catch (Exception e) {
            LOG.error(format("Error during publishing a task %s to PubSub topic", taskType), e);
        }
        return null;
    }
}
