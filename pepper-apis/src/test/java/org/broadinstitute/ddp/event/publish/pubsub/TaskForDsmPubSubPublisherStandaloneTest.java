package org.broadinstitute.ddp.event.publish.pubsub;

import static org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher.ATTR_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher.ATTR_STUDY_GUID;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask.ATTR_TASK_TYPE;
import static org.broadinstitute.ddp.model.activity.types.EventActionType.UPDATE_CUSTOM_WORKFLOW;
import static org.broadinstitute.ddp.model.event.UpdateCustomWorkflowEventAction.generatePayload;
import static org.junit.Assert.assertEquals;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.ConfigAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.util.ConfigManager;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Test event publishing/subscribing with PubSub emulator.
 * In order to eun this test it needs to start PubSub emulator (locally), for example to port to localhost:8085.
 * Create a topic `local-dss-to-dsm-tasks`.
 * Create a subscription `local-dss-to-dsm-tasks-subs`.
 * In IntelliJ config add env variable: PUBSUB_EMULATOR_HOST=localhost:8085.
 * Comment annotation @Ignore.
 * Run test `testEventPublisherSubscriber()`.
 */
public class TaskForDsmPubSubPublisherStandaloneTest extends ConfigAwareBaseTest {

    private static final Logger LOG = getLogger(TaskForDsmPubSubPublisherStandaloneTest.class);

    private static final Config conf = ConfigManager.getInstance().getConfig();
    private static final String TEST_SUBSCRIPTION = conf.getString(ConfigFile.PUBSUB_DSM_TASK_TOPIC) + "-subs";

    private static String expectedTaskType;
    private static String expectedStudyGuid;
    private static String expectedParticipantGuid;
    private static String expectedPayloadJson;

    @Test
    @Ignore
    public void testDsmTaskPublisherSubscriber() throws InterruptedException {
        var userId = 1L;
        var userGuid = "USER_GUID";
        var studyId = 2L;
        var studyGuid = "STUDY_GUID";

        var signal = new EventSignal(userId, userId, userGuid, userGuid, studyId, studyGuid, EventTriggerType.ACTIVITY_STATUS);

        var workflow = "Workflow1";
        var status = "Registered";

        var taskPublisher = new TaskPubSubPublisher();

        taskPublisher.publishTask(
                UPDATE_CUSTOM_WORKFLOW.name(),
                generatePayload(workflow, status),
                signal.getStudyGuid(),
                signal.getParticipantGuid()
        );

        createTestSubscriber();

        Thread.sleep(10000);

        assertEquals(UPDATE_CUSTOM_WORKFLOW.name(), expectedTaskType);
        assertEquals(studyGuid, expectedStudyGuid);
        assertEquals(userGuid, expectedParticipantGuid);
        assertEquals("{\"workflow\":\"Workflow1\",\"status\":\"Registered\"}", expectedPayloadJson);
    }

    private void createTestSubscriber() {
        var executorProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(10).build();
        var callbackExecutor = Executors.newSingleThreadExecutor();

        var pubSubTaskReceiver = new PubSubTaskReceiver();

        Subscriber pubSubTaskSubscriber = null;
        var projectSubscriptionName = ProjectSubscriptionName.of(
                conf.getString(ConfigFile.GOOGLE_PROJECT_ID), TEST_SUBSCRIPTION);

        try {
            pubSubTaskSubscriber = PubSubPublisherInitializer.getPubsubConnectionManager().subscribeBuilder(
                    projectSubscriptionName, pubSubTaskReceiver)
                    .setExecutorProvider(executorProvider)
                    .setSystemExecutorProvider(executorProvider)
                    .build();
            pubSubTaskSubscriber.addListener(
                    new Subscriber.Listener() {
                        public void failed(Subscriber.State from, Throwable failure) {
                            LOG.error("Error consume a message", failure);
                        }
                    },
                    callbackExecutor);
            pubSubTaskSubscriber.startAsync().awaitRunning(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pubSubTaskSubscriber.stopAsync();
            throw new DDPException("Could not start subscriber for subscription" + projectSubscriptionName.getSubscription(), e);
        }
    }

    public static class PubSubTaskReceiver implements MessageReceiver {
        @Override
        public void receiveMessage(PubsubMessage pubsubMessage, AckReplyConsumer ackReplyConsumer) {
            parseMessage(pubsubMessage);
        }
    }

    private static void parseMessage(PubsubMessage message) {
        expectedTaskType = message.getAttributesOrDefault(ATTR_TASK_TYPE, null);
        expectedStudyGuid = message.getAttributesOrDefault(ATTR_STUDY_GUID, null);
        expectedParticipantGuid = message.getAttributesOrDefault(ATTR_PARTICIPANT_GUID, null);
        expectedPayloadJson = message.getData() != null ? message.getData().toStringUtf8() : null;
    }
}
