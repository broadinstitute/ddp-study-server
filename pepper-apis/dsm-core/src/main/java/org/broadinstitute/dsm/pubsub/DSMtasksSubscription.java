package org.broadinstitute.dsm.pubsub;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.enums.DSMTaskType;
import org.broadinstitute.ddp.enums.PubSubAttributes;
import org.broadinstitute.dsm.db.DDPInstance;
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
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.ParticipantUtil;

@Slf4j
public class DSMtasksSubscription {
    private static final Map<String, Integer> retryPerParticipant = new ConcurrentHashMap<>();
    public static final String CLEAR_BEFORE_UPDATE = "clearBeforeUpdate";
    public static final int MAX_RETRY = 50;

    public static void subscribeDSMtasks(String projectId, String subscriptionId, NotificationUtil notificationUtil) {
        // Instantiate an asynchronous message receiver.
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            // Handle incoming message, then ack the received message.
            log.info("Got message with Id: " + message.getMessageId());
            Map<String, String> attributesMap = message.getAttributesMap();
            String data = Optional.of(message).map(PubsubMessage::getData).map(ByteString::toStringUtf8).orElse(null);
            DSMTaskType taskType = DSMTaskType.of(attributesMap.get(PubSubAttributes.TASK_TYPE.getValue()));

            log.info("Task type is: " + taskType);

            if (taskType == null) {
                log.warn("The message {} doesn't have task type specified so it can't be processed", message);
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
                    case FILE_UPLOADED:
                        sendFileUploadedNotification(notificationUtil,
                                message.getAttributesOrDefault(PubSubAttributes.STUDY_GUID.getValue(), null),
                                message.getAttributesOrDefault(PubSubAttributes.FILE_NAME.getValue(), null));
                        consumer.ack();
                        break;
                    default:
                        log.warn("The message {} contains incorrect task type {} so it can't be processed", message, taskType);
                        consumer.ack();
                        break;
                }
            }
        };
        Subscriber subscriber;
        ProjectSubscriptionName resultSubName = ProjectSubscriptionName.of(projectId, subscriptionId);
        ExecutorProvider resultsSubExecProvider = InstantiatingExecutorProvider.newBuilder().setExecutorThreadCount(1).build();
        subscriber = Subscriber.newBuilder(resultSubName, receiver).setParallelPullCount(1).setExecutorProvider(resultsSubExecProvider)
                .setMaxAckExtensionPeriod(org.threeten.bp.Duration.ofSeconds(120)).build();
        try {
            subscriber.startAsync().awaitRunning(1L, TimeUnit.MINUTES);
            log.info("Started pubsub subscription receiver DSM tasks subscription");
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out while starting pubsub subscription for DSM tasks", e);
        }
    }

    private static void migrateToES(ExportToES.ExportPayload exportPayload) {
        String study = exportPayload.getStudy();
        Optional<DDPInstanceDto> maybeDdpInstanceByInstanceName = new DDPInstanceDao().getDDPInstanceByInstanceName(study);
        maybeDdpInstanceByInstanceName.ifPresent(ddpInstanceDto -> {
            String index = ddpInstanceDto.getEsParticipantIndex();
            log.info("Starting migrating DSM data to ES for study: " + study + " with index: " + index);
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

    private static void sendFileUploadedNotification(final NotificationUtil notificationUtil, final String studyGuid, final String fileName) {
        if (StringUtils.isBlank(fileName)) {
            log.error("Can't send file uploaded notification");
            return;
        }

        notificationUtil.sentNotification(DDPInstance.getDDPInstanceByGuid(studyGuid).getNotificationRecipient(),
                String.format("Somebody uploaded a new file: %s in terms of %s study", fileName, studyGuid),
                NotificationUtil.UNIVERSAL_NOTIFICATION_TEMPLATE,
                "A new file uploaded");
    }
}
