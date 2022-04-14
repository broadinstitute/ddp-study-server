package org.broadinstitute.ddp.event.pubsubtask.api;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.housekeeping.PubSubConnectionManager;

/**
 * Creates PubSubTask Subscriber
 * (reading messages from PubSubTask subscription defined by config param "pubsub.pubSubTasksSubscription")
 * and PubSubTask result Publisher
 * (publishing results to topic defined by config param "pubsub.pubSubTasksResultTopic").
 *
 * <p>Creates {@link PubSubTaskReceiver} which process PubSubTask-messages (coming from PubSubTask subscription):
 * parses it and runs corresponding {@link PubSubTaskProcessor} which executes needed actions according to
 * PubSubTask 'taskType' (each processor implemented for a specific 'taskType').
 * After doing an action it is called the {@link PubSubTaskResultSender}
 * which sends result to a topic "pubsub.pubSubTasksResultTopic".
 */
@Slf4j
public class PubSubTaskConnectionService {
    public static final int DEFAULT_SUBSCRIBER_AWAIT_RUNNING_TIMEOUT_SEC = 30;
    public static final int SUBSCRIBER_FAILURE_LISTENER_THREAD_COUNT = 4;
    public static final int PUBLISHER_AWAIT_TERMINATION_TIMEOUT_MIN = 1;

    private final PubSubConnectionManager pubSubConnectionManager;
    private final ProjectSubscriptionName projectSubscriptionName;
    private final ProjectTopicName pubSubTaskResultProjectTopicName;
    private final PubSubTaskProcessorFactory pubSubTaskProcessorFactory;
    private final int subscriberAwaitRunningTimeout;

    private PubSubTaskReceiver pubSubTaskReceiver;
    private PubSubTaskResultSender pubSubTaskResultSender;

    private Subscriber pubSubTaskSubscriber;
    private Publisher pubSubTaskResultPublisher;


    public PubSubTaskConnectionService(
            PubSubConnectionManager pubSubConnectionManager,
            String projectId,
            String subscription,
            String pubSubTaskResultPubSubTopic,
            int subscriberAwaitRunningTimeout,
            PubSubTaskProcessorFactory pubSubTaskProcessorFactory) {
        this.pubSubConnectionManager = pubSubConnectionManager;
        projectSubscriptionName = ProjectSubscriptionName.of(projectId, subscription);
        pubSubTaskResultProjectTopicName = ProjectTopicName.of(projectId, pubSubTaskResultPubSubTopic);
        this.subscriberAwaitRunningTimeout = subscriberAwaitRunningTimeout;
        this.pubSubTaskProcessorFactory = pubSubTaskProcessorFactory;
    }

    public void create() {
        try {
            createPubSubTaskResultPublisher();
            pubSubTaskResultSender = new PubSubTaskResultSender(pubSubTaskResultPublisher);
            pubSubTaskReceiver = new PubSubTaskReceiver(projectSubscriptionName, pubSubTaskProcessorFactory, pubSubTaskResultSender);

            var executorProvider =
                    InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(SUBSCRIBER_FAILURE_LISTENER_THREAD_COUNT).build();
            var callbackExecutor = Executors.newSingleThreadExecutor();

            createPubSubTaskSubscriber(executorProvider, callbackExecutor);

        } catch (Exception e) {
            throw new PubSubTaskException(errorMsg("Failed to create PubSubTask connection"), e);
        }
    }

    public void destroy() {
        if (pubSubTaskSubscriber != null && pubSubTaskSubscriber.isRunning()) {
            pubSubTaskSubscriber.stopAsync();
        }
        if (pubSubTaskResultPublisher != null) {
            try {
                pubSubTaskResultPublisher.shutdown();
                pubSubTaskResultPublisher.awaitTermination(PUBLISHER_AWAIT_TERMINATION_TIMEOUT_MIN, TimeUnit.MINUTES);
            } catch (Exception e) {
                throw new PubSubTaskException(errorMsg("Failed to shutdown PubSubTask connection"), e);
            }
        }
    }

    private void createPubSubTaskSubscriber(ExecutorProvider executorProvider, ExecutorService callbackExecutor) {
        try {
            pubSubTaskSubscriber = pubSubConnectionManager.subscribeBuilder(projectSubscriptionName, pubSubTaskReceiver)
                    .setExecutorProvider(executorProvider)
                    .setSystemExecutorProvider(executorProvider)
                    .build();

            pubSubTaskSubscriber.addListener(
                    new Subscriber.Listener() {
                        public void failed(Subscriber.State from, Throwable failure) {
                            log.error(errorMsg("Unrecoverable failure happened during subscribing to subscription "
                                    + projectSubscriptionName), failure);
                            if (!executorProvider.getExecutor().isShutdown()) {
                                createPubSubTaskSubscriber(executorProvider, callbackExecutor);
                            }
                        }
                    },
                    callbackExecutor);

            pubSubTaskSubscriber.startAsync().awaitRunning(subscriberAwaitRunningTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pubSubTaskSubscriber.stopAsync();
            throw new DDPException("Could not start subscriber for subscription"
                    + projectSubscriptionName.getSubscription(), e);
        }
        log.info(infoMsg("Subscriber to subscription {} is STARTED"), projectSubscriptionName);
    }

    public void createPubSubTaskResultPublisher() {
        try {
            pubSubTaskResultPublisher = pubSubConnectionManager.getOrCreatePublisher(pubSubTaskResultProjectTopicName);
        } catch (IOException e) {
            throw new DDPException("Could not create publisher for topic "
                    + pubSubTaskResultProjectTopicName.getTopic(), e);
        }
    }
}
