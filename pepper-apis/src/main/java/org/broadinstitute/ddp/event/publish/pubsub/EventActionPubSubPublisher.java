package org.broadinstitute.ddp.event.publish.pubsub;

import static java.lang.String.format;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;

import java.io.IOException;
import java.util.Map;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.event.publish.EventActionPublisher;
import org.broadinstitute.ddp.housekeeping.PubSubConnectionManager;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link EventActionPublisher} providing publishing
 * an event of a specified type to Google PubSub topic.<br>
 * The parameters for PubSub publishing are taken from config.
 * <b>Event publishing config parameters</b>:
 * <ul>
 *     <li>`usePubSubEmulator` - (true or false)</li>
 *     <li>`googleProjectId` - project ID (for a pubsub topic)</li>
 *     <li>`pubsub.eventActionTopic` - name of PubSub topic where to publish event</li>
 * </ul>
 */
public class EventActionPubSubPublisher implements EventActionPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(EventActionPubSubPublisher.class);

    public static final String ATTR_STUDY_GUID = "studyGuid";
    public static final String ATTR_PARTICIPANT_GUID = "participantGuid";

    private final Config cfg;
    private final PubSubConnectionManager pubsubConnectionManager;
    private final Publisher eventPublisher;
    private final ProjectTopicName pubSubTopicName;


    /**
     * Constructor. Creates PubSub topic for publishing events.
     */
    public EventActionPubSubPublisher() throws IOException {
        this.cfg = ConfigManager.getInstance().getConfig();
        final boolean usePubSubEmulator = cfg.getBoolean(ConfigFile.USE_PUBSUB_EMULATOR);
        this.pubsubConnectionManager = new PubSubConnectionManager(usePubSubEmulator);
        this.pubSubTopicName = ProjectTopicName.of(
                cfg.getString(ConfigFile.GOOGLE_PROJECT_ID),
                cfg.getString(ConfigFile.PUBSUB_EVENT_ACTION_TOPIC));
        this.eventPublisher = pubsubConnectionManager.getOrCreatePublisher(pubSubTopicName);
    }

    @Override
    public void publishEventAction(
            EventActionType eventActionType,
            String eventPayload,
            String studyGuid,
            String participantGuid) {

        PubsubMessage pubSubMessage = createPubSubMessage(eventActionType, eventPayload, studyGuid, participantGuid);

        final String message = format(" event %s to pubsub topic=%s: studyGuid=%s, participantGuid=%s, payload={%s}",
                eventActionType, eventPublisher.getTopicName(), studyGuid, participantGuid, eventPayload);

        LOG.info("Publish" + message);

        ApiFuture<String> publishResult = eventPublisher.publish(pubSubMessage);

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
               EventActionType eventActionType,
               String eventPayload,
               String studyGuid,
               String participantGuid) {
        var messageBuilder = PubsubMessage.newBuilder();
        messageBuilder.setData(ByteString.copyFromUtf8(eventPayload));
        messageBuilder.putAllAttributes(Map.of(
                ATTR_TASK_TYPE, eventActionType.name(),
                ATTR_STUDY_GUID, studyGuid,
                ATTR_PARTICIPANT_GUID, participantGuid
        ));
        return messageBuilder.build();
    }
}
