package org.broadinstitute.ddp.event.dsmtask.api;

import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.errorMsg;
import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.infoMsg;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.housekeeping.PubSubConnectionManager;
import org.slf4j.Logger;

/**
 * Creates DSM Task Subscriber (reading messages from subscription "dsm-to-dss-tasks-sub")
 * and DSM Task result Publisher (publishing results to topic "dss-to-dsm-results").
 *
 * <p>Creates {@link DsmTaskReceiver} which receives messages with DSM tasks, parses it and
 * runs corresponding {@link DsmTaskProcessor} which does needed actions according to
 * DSM task 'taskType' (each processor implemented to a specific 'taskType').
 * After doing an action it is called the {@link DsmTaskResultSender}
 * which sends result to topic "dss-to-dsm-results".
 */
public class DsmTaskPubSubConnectionCreator {

    private static final Logger LOG = getLogger(DsmTaskPubSubConnectionCreator.class);

    public static final int DEFAULT_SUBSCRIBER_AWAIT_RUNNING_TIMEOUT_SEC = 30;
    public static final int SUBSCRIBER_FAILURE_LISTENER_THREAD_COUNT = 4;
    public static final int PUBLISHER_AWAIT_TERMINATION_TIMEOUT_MIN = 1;

    private final PubSubConnectionManager pubSubConnectionManager;
    private final ProjectSubscriptionName projectSubscriptionName;
    private final ProjectTopicName dsmTaskResultProjectTopicName;
    private final DsmTaskProcessorFactory dsmTaskProcessorFactory;
    private final int subscriberAwaitRunningTimeout;

    private DsmTaskReceiver dsmTaskReceiver;
    private DsmTaskResultSender dsmTaskResultSender;

    private Subscriber dsmTaskSubscriber;
    private Publisher dsmTaskResultPublisher;


    public DsmTaskPubSubConnectionCreator(
            PubSubConnectionManager pubSubConnectionManager,
            String projectId,
            String subscription,
            String dsmTaskResultPubSubTopic,
            int subscriberAwaitRunningTimeout,
            DsmTaskProcessorFactory dsmTaskProcessorFactory) {
        this.pubSubConnectionManager = pubSubConnectionManager;
        projectSubscriptionName = ProjectSubscriptionName.of(projectId, subscription);
        dsmTaskResultProjectTopicName = ProjectTopicName.of(projectId, dsmTaskResultPubSubTopic);
        this.subscriberAwaitRunningTimeout = subscriberAwaitRunningTimeout;
        this.dsmTaskProcessorFactory = dsmTaskProcessorFactory;
    }

    public void create() {
        try {
            createDsmTaskResultPubSubTopicPublisher();
            dsmTaskResultSender = new DsmTaskResultSender(dsmTaskResultPublisher);
            dsmTaskReceiver = new DsmTaskReceiver(projectSubscriptionName, dsmTaskProcessorFactory, dsmTaskResultSender);
            createDsmTaskPubSubTopicSubscriber();
        } catch (Exception e) {
            LOG.error(errorMsg("Failed to create DsmTask pubsub connection"), e);
        }
    }

    public void destroy() {
        if (dsmTaskSubscriber != null && dsmTaskSubscriber.isRunning()) {
            dsmTaskSubscriber.stopAsync();
        }
        if (dsmTaskResultPublisher != null) {
            try {
                dsmTaskResultPublisher.shutdown();
                dsmTaskResultPublisher.awaitTermination(PUBLISHER_AWAIT_TERMINATION_TIMEOUT_MIN, TimeUnit.MINUTES);
            } catch (Exception e) {
                LOG.error(errorMsg("Failed to shutdown DsmTask pubsub connection"), e);
            }
        }
    }

    private void createDsmTaskPubSubTopicSubscriber() {
        try {
            // Provides an executor service for processing messages.
            ExecutorProvider executorProvider =
                    InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(SUBSCRIBER_FAILURE_LISTENER_THREAD_COUNT).build();

            dsmTaskSubscriber = pubSubConnectionManager.subscribeBuilder(projectSubscriptionName, dsmTaskReceiver)
                    .setParallelPullCount(1)
                    .setExecutorProvider(InstantiatingExecutorProvider.newBuilder()
                            .setExecutorThreadCount(1)
                            .build())
                    .build();

            // Listen for unrecoverable failures. Rebuild a subscriber and restart subscribing
            // when the current subscriber encounters permanent errors.
            dsmTaskSubscriber.addListener(
                    new Subscriber.Listener() {
                        public void failed(Subscriber.State from, Throwable failure) {
                            System.out.println(failure.getStackTrace());
                            if (!executorProvider.getExecutor().isShutdown()) {
                                createDsmTaskPubSubTopicSubscriber();
                            }
                        }
                    },
                    MoreExecutors.directExecutor());

            dsmTaskSubscriber.startAsync().awaitRunning(subscriberAwaitRunningTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // Shut down the subscriber after specified timeout (default=30s). Stop receiving messages.
            dsmTaskSubscriber.stopAsync();
            throw new DDPException("Could not start subscriber for subscription"
                    + projectSubscriptionName.getSubscription(), e);
        }
        LOG.info(infoMsg("Subscriber to subscription {} is STARTED"), projectSubscriptionName);
    }

    public void createDsmTaskResultPubSubTopicPublisher() {
        try {
            dsmTaskResultPublisher = pubSubConnectionManager.getOrCreatePublisher(dsmTaskResultProjectTopicName);
        } catch (IOException e) {
            throw new DDPException("Could not create publisher for topic "
                    + dsmTaskResultProjectTopicName.getTopic(), e);
        }
    }
}
