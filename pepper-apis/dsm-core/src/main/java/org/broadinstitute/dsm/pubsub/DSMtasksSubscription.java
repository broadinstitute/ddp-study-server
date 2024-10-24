package org.broadinstitute.dsm.pubsub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.export.ExportToES;
import org.broadinstitute.dsm.model.defaultvalues.Defaultable;
import org.broadinstitute.dsm.model.defaultvalues.DefaultableMaker;
import org.broadinstitute.dsm.model.elastic.migration.StudyMigrator;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DSMtasksSubscription {

    private static final Logger logger = LoggerFactory.getLogger(DSMtasksSubscription.class);
    public static final String TASK_TYPE = "taskType";
    public static final String CLEAR_BEFORE_UPDATE = "clearBeforeUpdate";
    public static final String UPDATE_CUSTOM_WORKFLOW = "UPDATE_CUSTOM_WORKFLOW";
    public static final String ELASTIC_EXPORT = "ELASTIC_EXPORT";
    public static final String PARTICIPANT_REGISTERED = "PARTICIPANT_REGISTERED";
    public static final int MAX_RETRY = 50;
    private static final Map<String, Integer> retryPerParticipant = new ConcurrentHashMap<>();

    public static Subscriber subscribeDSMtasks(String projectId, String subscriptionId) {
        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            // Handle incoming message, then ack the received message.
            logger.info("Got message with Id: {}", message.getMessageId());
            Map<String, String> attributesMap = message.getAttributesMap();
            String taskType = attributesMap.get(TASK_TYPE);
            String data = message.getData().toStringUtf8();

            logger.info("DSMtasksSubscription task type is: {}", taskType);

            if (StringUtils.isBlank(taskType)) {
                logger.error("Task type from pubsub is missing");
                consumer.ack();
            } else {
                try {
                    switch (taskType) {
                        case UPDATE_CUSTOM_WORKFLOW:
                            consumer.ack();
                            runCustomWorkflow(attributesMap, data);
                            break;
                        case ELASTIC_EXPORT:
                            consumer.ack();
                            doESExport(attributesMap, data);
                            break;
                        case PARTICIPANT_REGISTERED:
                            generateStudyDefaultValues(consumer, attributesMap, data);
                            break;
                        default:
                            logger.error("Invalid task type DSMtasksSubscription PubsubMessage: {}", taskType);
                            consumer.ack();
                            break;
                    }
                } catch (Exception e) {
                    logger.error("Error handling PubsubMessage for {} with DSMtasksSubscription: {}", taskType, e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        ProjectSubscriptionName resultSubName = ProjectSubscriptionName.of(projectId, subscriptionId);
        ExecutorProvider resultsSubExecProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();
        Subscriber subscriber = Subscriber.newBuilder(resultSubName, receiver)
                .setParallelPullCount(1)
                .setExecutorProvider(resultsSubExecProvider)
                .setMaxAckExtensionPeriod(org.threeten.bp.Duration.ofSeconds(120)).build();
        try {
            subscriber.startAsync().awaitRunning(1L, TimeUnit.MINUTES);
            logger.info("Started pubsub subscription receiver DSM tasks subscription");
        } catch (TimeoutException e) {
            throw new DsmInternalError("Timed out while starting pubsub subscription for DSM tasks", e);
        }
        return subscriber;
    }

    public static void doESExport(Map<String, String> attributesMap, String data) {
        ExportToES.ExportPayload exportPayload = new Gson().fromJson(data, ExportToES.ExportPayload.class);
        if (exportPayload.isMigration()) {
            migrateToES(exportPayload);
        } else {
            boolean clearBeforeUpdate = attributesMap.containsKey(CLEAR_BEFORE_UPDATE) && Boolean.parseBoolean(
                    attributesMap.get(CLEAR_BEFORE_UPDATE));
            new ExportToES().exportObjectsToES(data, clearBeforeUpdate);
        }
    }

    public static void migrateToES(ExportToES.ExportPayload exportPayload) {
        StudyMigrator.migrate(exportPayload.getStudy(), null);
    }

    private static void generateStudyDefaultValues(AckReplyConsumer consumer, Map<String, String> attributesMap,
                                                   String payload) {
        String studyGuid = attributesMap.get("studyGuid");
        if (StringUtils.isEmpty(studyGuid)) {
            throw new DSMBadRequestException("No studyGuid provided");
        }
        Defaultable defaultable = DefaultableMaker.makeDefaultable(studyGuid);
        if (defaultable == null) {
            logger.info("No study default values generator for {}", studyGuid);
            consumer.ack();
            return;
        }
        String participantGuid = attributesMap.get("participantGuid");
        if (!ParticipantUtil.isGuid(participantGuid)) {
            consumer.ack();
            throw new DsmInternalError("Invalid participant GUID: " + participantGuid);
        }

        try {
            defaultable.generateDefaults(studyGuid, participantGuid, payload);
            retryPerParticipant.remove(participantGuid);
            consumer.ack();
        } catch (ESMissingParticipantDataException e) {
            // retry until ES data shows up, or we reach max wait
            retryPerParticipant.merge(participantGuid, 1, Integer::sum);
            int tryNum = retryPerParticipant.get(participantGuid);
            if (tryNum == MAX_RETRY) {
                logger.warn("Exhausted retries waiting for missing ES data for participant {}", participantGuid, e);
                retryPerParticipant.remove(participantGuid);
                consumer.ack();
                throw e;
            } else {
                logger.info("Waiting for missing ES data for participant {}: {}. Try number {}",
                        participantGuid, e, tryNum);
                consumer.nack();
            }
        }
    }

    /**
     * Handling the custom workflow may be slow (DB and ES updates in the handler), and we may have to wait for ES
     * updates to complete, so give this a thread for the work
     */
    private static void runCustomWorkflow(Map<String, String> attributes, String payload) {
        Thread t = new Thread(new RunCustomWorkflow(attributes, payload));
        t.start();
    }

    private static class RunCustomWorkflow implements Runnable {
        private static final int NUM_RETRIES = 10;
        private static final int SLEEP_TIME_SEC = 3;
        private final Map<String, String> attributes;
        private final String payload;

        public RunCustomWorkflow(Map<String, String> attributes, String payload) {
            this.attributes = attributes;
            this.payload = payload;
        }

        public void run() {
            for (int i = 1; i <= NUM_RETRIES; i++) {
                try {
                    WorkflowStatusUpdate.updateCustomWorkflow(attributes, payload);
                    return;
                } catch (ESMissingParticipantDataException e) {
                    // ES data update jobs may not have completed yet
                    logger.info("Waiting for missing ES data, try number {}: {}", i, e.toString());
                    sleep();
                } catch (Exception e) {
                    logger.error("Error handling UPDATE_CUSTOM_WORKFLOW: {}", e.toString());
                    e.printStackTrace();
                    return;
                }
            }
            logger.error("Error handling UPDATE_CUSTOM_WORKFLOW: Participant ES data not available after waiting {} seconds",
                    NUM_RETRIES * SLEEP_TIME_SEC);
        }

        private void sleep() {
            try {
                TimeUnit.SECONDS.sleep(SLEEP_TIME_SEC);
            } catch (InterruptedException e) {
                logger.error("Unexpected InterruptedException", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}
