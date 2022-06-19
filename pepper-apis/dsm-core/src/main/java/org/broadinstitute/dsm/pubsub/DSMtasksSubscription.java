package org.broadinstitute.dsm.pubsub;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.export.ExportToES;
import org.broadinstitute.dsm.model.Study;
import org.broadinstitute.dsm.model.defaultvalues.Defaultable;
import org.broadinstitute.dsm.model.defaultvalues.DefaultableMaker;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.migration.AdditionalParticipantMigratorFactory;
import org.broadinstitute.dsm.model.elastic.migration.CohortTagMigrator;
import org.broadinstitute.dsm.model.elastic.migration.DynamicFieldsMappingMigrator;
import org.broadinstitute.dsm.model.elastic.migration.KitRequestShippingMigrator;
import org.broadinstitute.dsm.model.elastic.migration.MedicalRecordMigrator;
import org.broadinstitute.dsm.model.elastic.migration.OncHistoryDetailsMigrator;
import org.broadinstitute.dsm.model.elastic.migration.OncHistoryMigrator;
import org.broadinstitute.dsm.model.elastic.migration.ParticipantDataMigrator;
import org.broadinstitute.dsm.model.elastic.migration.ParticipantMigrator;
import org.broadinstitute.dsm.model.elastic.migration.SMIDMigrator;
import org.broadinstitute.dsm.model.elastic.migration.TissueMigrator;
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
    private static Map<String, Integer> retryPerParticipant = new ConcurrentHashMap<>();

    public static void subscribeDSMtasks(String projectId, String subscriptionId) {
        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            // Handle incoming message, then ack the received message.
            logger.info("Got message with Id: " + message.getMessageId());
            Map<String, String> attributesMap = message.getAttributesMap();
            String taskType = attributesMap.get(TASK_TYPE);
            String data = message.getData() != null ? message.getData().toStringUtf8() : null;

            logger.info("Task type is: " + taskType);

            if (StringUtils.isBlank(taskType)) {
                logger.warn("task type from pubsub was missing");
                consumer.ack();
            } else {
                switch (taskType) {
                    case UPDATE_CUSTOM_WORKFLOW:
                        consumer.ack();
                        WorkflowStatusUpdate.updateCustomWorkflow(attributesMap, data);
                        break;
                    case ELASTIC_EXPORT:
                        consumer.ack();
                        ExportToES.ExportPayload exportPayload = new Gson().fromJson(data, ExportToES.ExportPayload.class);
                        if (exportPayload.isMigration()) {
                            migrateToES(exportPayload);
                        } else {
                            boolean clearBeforeUpdate = attributesMap.containsKey(CLEAR_BEFORE_UPDATE) && Boolean.parseBoolean(
                                    attributesMap.get(CLEAR_BEFORE_UPDATE));
                            new ExportToES().exportObjectsToES(data, clearBeforeUpdate);
                        }
                        break;
                    case PARTICIPANT_REGISTERED:
                        generateStudyDefaultValues(consumer, attributesMap);
                        break;
                    default:
                        logger.warn("Wrong task type for a message from pubsub");
                        consumer.ack();
                        break;
                }
            }
        };
        Subscriber subscriber = null;
        ProjectSubscriptionName resultSubName = ProjectSubscriptionName.of(projectId, subscriptionId);
        ExecutorProvider resultsSubExecProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();
        subscriber = Subscriber.newBuilder(resultSubName, receiver).setParallelPullCount(1).setExecutorProvider(resultsSubExecProvider)
                .setMaxAckExtensionPeriod(org.threeten.bp.Duration.ofSeconds(120)).build();
        try {
            subscriber.startAsync().awaitRunning(1L, TimeUnit.MINUTES);
            logger.info("Started pubsub subscription receiver DSM tasks subscription");
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out while starting pubsub subscription for DSM tasks", e);
        }
    }

    private static void migrateToES(ExportToES.ExportPayload exportPayload) {
        String study = exportPayload.getStudy();
        Optional<DDPInstanceDto> maybeDdpInstanceByInstanceName = new DDPInstanceDao().getDDPInstanceByInstanceName(study);
        maybeDdpInstanceByInstanceName.ifPresent(ddpInstanceDto -> {
            String index = ddpInstanceDto.getEsParticipantIndex();
            logger.info("Starting migrating DSM data to ES for study: " + study + " with index: " + index);
            List<? extends Exportable> exportables = Arrays.asList(
                    //DynamicFieldsMappingMigrator should be first in the list to make sure that mapping will be exported for first
                    new DynamicFieldsMappingMigrator(index, study), new MedicalRecordMigrator(index, study),
                    new OncHistoryDetailsMigrator(index, study), new OncHistoryMigrator(index, study),
                    new ParticipantDataMigrator(index, study), AdditionalParticipantMigratorFactory.of(index, study),
                    new ParticipantMigrator(index, study), new KitRequestShippingMigrator(index, study),
                    new TissueMigrator(index, study), new SMIDMigrator(index, study),
                    new CohortTagMigrator(index, study, new CohortTagDaoImpl()));
            exportables.forEach(Exportable::export);
        });
    }

    private static void generateStudyDefaultValues(AckReplyConsumer consumer, Map<String, String> attributesMap) {
        String studyGuid = attributesMap.get("studyGuid");
        String participantGuid = attributesMap.get("participantGuid");
        if (!ParticipantUtil.isGuid(participantGuid)) {
            consumer.ack();
            return;
        }
        ;
        Arrays.stream(Study.values()).filter(study -> study.toString().equals(studyGuid.toUpperCase())).findFirst()
                .ifPresentOrElse(study -> {
                    Defaultable defaultable = DefaultableMaker.makeDefaultable(study);
                    boolean result = defaultable.generateDefaults(studyGuid, participantGuid);
                    if (!result) {
                        retryPerParticipant.merge(participantGuid, 1, Integer::sum);
                        if (retryPerParticipant.get(participantGuid) == MAX_RETRY) {
                            retryPerParticipant.remove(participantGuid);
                            consumer.ack();
                        } else {
                            consumer.nack();
                        }
                    } else {
                        retryPerParticipant.remove(participantGuid);
                        consumer.ack();
                    }
                }, consumer::ack);
    }
}
