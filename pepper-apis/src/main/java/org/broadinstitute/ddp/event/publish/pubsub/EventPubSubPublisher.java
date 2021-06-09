package org.broadinstitute.ddp.event.publish.pubsub;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;

import java.util.Map;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.event.publish.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of {@link EventPublisher} providing publishing
 * an event of a specified type to Google PubSub topic.<br>
 * The parameters for PubSub publishing are taken from config:
 * `pubsub.eventActionTopic` - name of PubSub topic where to publish event.
 */
public class EventPubSubPublisher implements EventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(EventPubSubPublisher.class);

    public static final String ATTR_STUDY_GUID = "studyGuid";
    public static final String ATTR_PARTICIPANT_GUID = "participantGuid";

    @Override
    public void publishEvent(
            String eventType,
            String payload,
            String studyGuid,
            String participantGuid) {

        var publisherData = PubSubPublisherInitializer.getOrCreatePubSubPublisherData(
                ConfigFile.PUBSUB_EVENT_ACTION_TOPIC);

        PubsubMessage pubSubMessage = createPubSubMessage(eventType, payload, studyGuid, participantGuid);

        final String message = format(" event %s to pubsub topic=%s: studyGuid=%s, participantGuid=%s, payload={%s}",
                eventType, publisherData.getPubSubTopicName().getTopic(), studyGuid, participantGuid, payload);

        LOG.info("Publish" + message);

        ApiFuture<String> publishResult = publisherData.getPublisher().publish(pubSubMessage);

        ApiFutures.addCallback(
                publishResult,
                new ApiFutureCallback<>() {

                    @Override
                    public void onFailure(Throwable e) {
                        String statusCode = null;
                        if (e instanceof ApiException) {
                            statusCode = ((ApiException) e).getStatusCode().getCode().toString();
                        }
                        String msg = "Failed to publish" + message;
                        if (statusCode != null) {
                            msg += ", statusCode=" + statusCode;
                        }
                        LOG.error(msg, e);
                    }

                    @Override
                    public void onSuccess(String messageId) {
                        LOG.info("Successfully published" + message);
                    }
                },
                MoreExecutors.directExecutor()
        );
    }

    private PubsubMessage createPubSubMessage(
               String eventType,
               String eventPayload,
               String studyGuid,
               String participantGuid) {
        var messageBuilder = PubsubMessage.newBuilder();
        messageBuilder.setData(ByteString.copyFromUtf8(eventPayload));
        messageBuilder.putAllAttributes(Map.of(
                ATTR_TASK_TYPE, eventType,
                ATTR_STUDY_GUID, studyGuid,
                ATTR_PARTICIPANT_GUID, participantGuid
        ));
        return messageBuilder.build();
    }
}
