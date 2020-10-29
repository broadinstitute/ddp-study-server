package org.broadinstitute.ddp.event;

import java.io.IOException;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.housekeeping.schedule.OnDemandExportJob;
import org.broadinstitute.ddp.housekeeping.schedule.TemporaryUserCleanupJob;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JavaHeapDumper;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HousekeepingTaskReceiver implements MessageReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(HousekeepingTaskReceiver.class);
    private static final Gson gson = GsonUtil.standardGson();
    private static final String ATTR_TYPE = "type";

    private final ProjectSubscriptionName subName;
    private final Scheduler scheduler;

    public HousekeepingTaskReceiver(ProjectSubscriptionName subName, Scheduler scheduler) {
        this.subName = subName;
        this.scheduler = scheduler;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer reply) {
        String rawType = message.getAttributesOrDefault(ATTR_TYPE, null);
        LOG.info("Received pubsub task message {} with type '{}'", message.getMessageId(), rawType);

        TaskType type;
        try {
            type = TaskType.valueOf(rawType.toUpperCase());
            LOG.info("Type of task message is: {}", type);
        } catch (Exception e) {
            LOG.error("Could not determine task type for message {}, ack-ing", message.getMessageId());
            reply.ack();
            return;
        }

        switch (type) {
            case PING:
                LOG.info("Received PING task message on subscription {}, ack-ing", subName);
                reply.ack();
                break;
            case CLEAR_CACHE:
                handleClearCache(message, reply);
                break;
            case CLEANUP_TEMP_USERS:
                handleCleanupTempUsers(message, reply);
                break;
            case CSV_EXPORT:
                handleCsvExport(message, reply);
                break;
            case ELASTIC_EXPORT:
                handleElasticExport(message, reply);
                break;
            case GENERATE_HEAP_DUMP:
                handleHeapDumpGeneration(message, reply);
                break;
            default:
                throw new DDPException("Unhandled task type: " + type);
        }
    }

    private void handleClearCache(PubsubMessage message, AckReplyConsumer reply) {
        LOG.info("Clearing caches now...");
        ActivityDefStore.getInstance().clear();
        DataExporter.clearCachedAuth0Emails();
        LOG.info("Finished clearing activity and email caches, ack-ing");
        reply.ack();
    }

    private void handleCleanupTempUsers(PubsubMessage message, AckReplyConsumer reply) {
        JobKey key = TemporaryUserCleanupJob.getKey();
        try {
            scheduler.triggerJob(key);
            LOG.info("Scheduled job {} for message {}, ack-ing", key, message.getMessageId());
            reply.ack();
        } catch (SchedulerException e) {
            LOG.error("Error triggering job {} for message {}, nack-ing", key, message.getMessageId(), e);
            reply.nack();
        }
    }

    private void handleCsvExport(PubsubMessage message, AckReplyConsumer reply) {
        String data = message.getData() != null ? message.getData().toStringUtf8() : null;
        var payload = gson.fromJson(data, ExportPayload.class);
        if (payload == null || payload.getStudy() == null) {
            LOG.error("Study needs to be provided for CSV_EXPORT task message, ack-ing");
            reply.ack();
            return;
        }

        JobDataMap map = new JobDataMap();
        map.put(OnDemandExportJob.DATA_STUDY, payload.getStudy());
        map.put(OnDemandExportJob.DATA_CSV, true);

        JobKey key = OnDemandExportJob.getKey();
        try {
            scheduler.triggerJob(key, map);
            LOG.info("Scheduled job {} for message {}, ack-ing", key, message.getMessageId());
            reply.ack();
        } catch (SchedulerException e) {
            LOG.error("Error triggering job {} for message {}, nack-ing", key, message.getMessageId(), e);
            reply.nack();
        }
    }

    private void handleElasticExport(PubsubMessage message, AckReplyConsumer reply) {
        String data = message.getData() != null ? message.getData().toStringUtf8() : null;
        var payload = gson.fromJson(data, ExportPayload.class);
        if (payload == null || payload.getStudy() == null) {
            LOG.error("Study needs to be provided for ELASTIC_EXPORT task message, ack-ing");
            reply.ack();
            return;
        }

        JobDataMap map = new JobDataMap();
        map.put(OnDemandExportJob.DATA_STUDY, payload.getStudy());
        if (payload.getIndex() == null) {
            map.put(OnDemandExportJob.DATA_INDEX, OnDemandExportJob.ALL_INDICES);
        } else {
            map.put(OnDemandExportJob.DATA_INDEX, payload.getIndex());
        }

        JobKey key = OnDemandExportJob.getKey();
        try {
            scheduler.triggerJob(key, map);
            LOG.info("Scheduled job {} for message {}, ack-ing", key, message.getMessageId());
            reply.ack();
        } catch (SchedulerException e) {
            LOG.error("Error triggering job {} for message {}, nack-ing", key, message.getMessageId(), e);
            reply.nack();
        }
    }

    private void handleHeapDumpGeneration(PubsubMessage message, AckReplyConsumer reply)  {
        LOG.info("Received heap dump request via message id {}", message.getMessageId());
        String projectId = ConfigManager.getInstance().getConfig().getString(ConfigFile.GOOGLE_PROJECT_ID);
        String bucketName = projectId + "-heap-dumps";
        try {
            new JavaHeapDumper().dumpHeapToBucket(projectId, bucketName);
            LOG.info("Heap dump requested via pub sub message id {} completed", message.getMessageId());
            reply.ack();
        } catch (IOException | DDPException e) {
            LOG.error("Error processing heap dump requested via message id {}. Nack-ing message ", message.getMessageId(), e);
            reply.nack();
        }
    }

    public enum TaskType {
        CLEAR_CACHE,
        CLEANUP_TEMP_USERS,
        CSV_EXPORT,
        ELASTIC_EXPORT,
        GENERATE_HEAP_DUMP,
        PING
    }

    public static class ExportPayload {
        private String index;
        private String study;

        public String getIndex() {
            return index;
        }

        public String getStudy() {
            return study;
        }
    }
}
