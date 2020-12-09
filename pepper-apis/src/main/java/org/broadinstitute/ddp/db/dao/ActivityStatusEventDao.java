package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ActivityStatusEventDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(ActivityStatusEventDao.class);

    @CreateSqlObject
    EventDao getEventDao();

    /**
     * Queries activity status-related event configuration and queues
     * up events for pubsub processing.
     *
     * @param operatorId    user_id of the operator currently using pepper
     * @param participantId user_id of the participant
     * @param instanceDto   the activity instance
     * @param status        the status that the activity is being changed to
     * @return the number of events that were queued
     */
    default int addStatusTriggerEventsToQueue(long operatorId,
                                              long participantId,
                                              ActivityInstanceDto instanceDto, String status) {
        int numEventsQueued = 0;
        List<EventConfigurationDto> eventConfigs = getEventDao()
                .getEventConfigurationDtosForActivityStatus(instanceDto.getId(), status);
        JdbiQueuedEvent jdbiQueuedEvent = getHandle().attach(JdbiQueuedEvent.class);
        QueuedEventDao queuedEventDao = getHandle().attach(QueuedEventDao.class);
        for (EventConfigurationDto eventConfig : eventConfigs) {
            Integer delayBeforePosting = eventConfig.getPostDelaySeconds();
            if (delayBeforePosting == null) {
                delayBeforePosting = 0;
            }
            long postAfter = Instant.now().getEpochSecond() + delayBeforePosting;

            Long queuedEventId = null;

            // if we're inserting a notification, add the instance guid to the list of substitutions,
            // since we know it at this point
            if (eventConfig.getEventActionType() == EventActionType.NOTIFICATION) {
                Map<String, String> templateSubstitutions = new HashMap<>();
                templateSubstitutions.put(NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID, instanceDto.getGuid());
                queuedEventId = queuedEventDao.insertNotification(eventConfig.getEventConfigurationId(),
                        postAfter, participantId, operatorId,
                        templateSubstitutions, null);
            } else {
                queuedEventId = jdbiQueuedEvent.insert(eventConfig.getEventConfigurationId(),
                        postAfter, participantId, operatorId);
            }
            LOG.info("Inserted queued event {} for configuration {}", queuedEventId,
                    eventConfig.getEventConfigurationId());

            numEventsQueued++;
        }
        return numEventsQueued;
    }
}
