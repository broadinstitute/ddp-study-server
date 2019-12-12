package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.db.DaoException;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface QueuedEventDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(QueuedEventDao.class);

    @CreateSqlObject
    JdbiQueuedEvent getJdbiQueuedEvent();

    @CreateSqlObject
    JdbiQueuedNotificationTemplateSubstitution getJdbiTemplateSubstitution();

    @CreateSqlObject
    JdbiQueuedNotification getJdbiQueuedNotification();

    /**
     * Deletes all queued events for the given event configuration id
     * @param eventConfigurationId the event configuration id
     * @return number of rows deleted
     */
    default int deleteQueuedEventsByEventConfigurationId(long eventConfigurationId) {
        List<Long> queuedEventsToDelete = getJdbiQueuedEvent()
                .findQueuedEventIdsByEventConfigurationId(eventConfigurationId);
        int numRowsDeleted = 0;
        for (Long queuedEventId : queuedEventsToDelete) {
            numRowsDeleted += deleteByQueuedEventId(queuedEventId);
        }
        LOG.info("Deleted {} queued events", numRowsDeleted);
        return numRowsDeleted;
    }

    /**
     * Deletes the queued event along with all dependencies
     */
    default int deleteByQueuedEventId(long queuedEventId) {
        // not checking counts on subclass tables--they might be empty
        getJdbiTemplateSubstitution().deleteByQueuedEventId(queuedEventId);
        getJdbiQueuedNotification().delete(queuedEventId);
        return getJdbiQueuedEvent().delete(queuedEventId);
    }

    default int deleteQueuedEventsByStudyId(long studyId) {
        JdbiQueuedEvent jdbiQueuedEvent = getJdbiQueuedEvent();
        jdbiQueuedEvent.deleteQueuedNotificationSubstitutionsByStudyId(studyId);
        jdbiQueuedEvent.deleteQueuedNotificationsByStudyId(studyId);
        return jdbiQueuedEvent.deleteQueuedEventsByStudyId(studyId);
    }

    /**
     * When sending notifications to users, please use {@link #insertNotification(long, Long, Long, Long, Map)} to
     * properly reference user rows.  This method is for sending emails to people other than users, such
     * as people on the stay informed mailing list.
     */
    default long insertNotification(long eventConfigurationId,
                                    long postAfterEpochSeconds,
                                    String toEmailAddress,
                                    Map<String, String> templateSubstitutions) {


        long queuedEventId = insertNotification(eventConfigurationId,
                postAfterEpochSeconds,
                null,
                null,
                templateSubstitutions);

        int numRowsUpdated = getJdbiQueuedNotification().updateEmailAddress(queuedEventId, toEmailAddress);

        if (numRowsUpdated != 1) {
            throw new DaoException("Updated " + numRowsUpdated + " queued notification rows with "
                    + toEmailAddress + " for queued event id " + queuedEventId + " instead of a single row.");
        }

        return queuedEventId;
    }

    default long insertNotification(long eventConfigurationId,
                                    Long postAfterEpochSeconds,
                                    Long participantId,
                                    Long operatorId,
                                    Map<String, String> templateSubstitutions) {

        // insert into base queued_event
        long queuedEventId = getJdbiQueuedEvent().insert(eventConfigurationId,
                postAfterEpochSeconds,
                participantId,
                operatorId);

        // insert into notification_queued event
        boolean inserted = getJdbiQueuedNotification().insert(queuedEventId);
        if (!inserted) {
            throw new DaoException("No rows inserted for queued notification");
        }
        JdbiQueuedNotificationTemplateSubstitution jdbiTemplateSubstitution = getJdbiTemplateSubstitution();


        for (Map.Entry<String, String> templateSubstitution : templateSubstitutions.entrySet()) {
            long substitutionId = jdbiTemplateSubstitution.insert(queuedEventId,
                    templateSubstitution.getKey(),
                    templateSubstitution.getValue());
        }
        return queuedEventId;
    }

    default long addToQueue(long eventConfigId, long operatorId, long participantId, Integer delaySecondsFromNow) {
        int delayBeforePosting = delaySecondsFromNow != null ? delaySecondsFromNow : 0;
        long postAfter = Instant.now().getEpochSecond() + delayBeforePosting;
        return getJdbiQueuedEvent().insert(eventConfigId, postAfter, participantId, operatorId);
    }
}
