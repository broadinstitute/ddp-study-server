package org.broadinstitute.ddp.event;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.schedule.DsmCancerLoaderJob;
import org.broadinstitute.ddp.schedule.DsmDrugLoaderJob;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventReceiver implements MessageReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(EventReceiver.class);
    private static final String TYPE_ATTR = "type";

    private final Scheduler scheduler;

    public EventReceiver(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer reply) {
        LOG.info("Received pubsub event message {}", message.getMessageId());

        EventType type;
        try {
            type = EventType.valueOf(message.getAttributesOrDefault(TYPE_ATTR, null));
            LOG.info("Type of event message is: {}", type);
        } catch (Exception e) {
            LOG.error("Could not determine event type for message {}, nack-ing", message.getMessageId());
            reply.nack();
            return;
        }

        switch (type) {
            case CLEAR_CACHE:
                handleClearCache(message, reply);
                break;
            case LOAD_DRUG_LIST:
                scheduleJobThenReply(message, reply, DsmDrugLoaderJob.getKey(), null);
                break;
            case LOAD_CANCER_LIST:
                scheduleJobThenReply(message, reply, DsmCancerLoaderJob.getKey(), null);
                break;
            default:
                throw new DDPException("Unhandled event type: " + type);
        }
    }

    private void handleClearCache(PubsubMessage message, AckReplyConsumer reply) {
        LOG.info("Clearing caches now...");
        ActivityDefStore.getInstance().clear();
        LOG.info("Finished clearing activity cache, ack-ing");
        reply.ack();
    }

    private void scheduleJobThenReply(PubsubMessage message, AckReplyConsumer reply, JobKey key, JobDataMap data) {
        try {
            if (data == null) {
                scheduler.triggerJob(key);
            } else {
                scheduler.triggerJob(key, data);
            }
            LOG.info("Scheduled job {} for message {}, ack-ing", key, message.getMessageId());
            reply.ack();
        } catch (SchedulerException e) {
            LOG.error("Error triggering job {} for message {}, nack-ing", key, message.getMessageId(), e);
            reply.nack();
        }
    }

    public enum EventType {
        CLEAR_CACHE,
        LOAD_DRUG_LIST,
        LOAD_CANCER_LIST,
    }
}
