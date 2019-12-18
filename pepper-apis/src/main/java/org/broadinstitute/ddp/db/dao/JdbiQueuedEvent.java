package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Set;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiQueuedEvent extends SqlObject {

    @SqlUpdate("insert into queued_event(event_configuration_id,post_after,participant_user_id,operator_user_id) "
            + "values(:configId,:postAfter,:participantId,:operatorId)")
    @GetGeneratedKeys
    long insert(@Bind("configId") long eventConfigurationId,
                @Bind("postAfter") Long postAfterEpochSeconds,
                @Bind("participantId") Long participantUserId,
                @Bind("operatorId") Long operatorUserId);

    /**
     * Consider using {@link #deleteAllByQueuedEventId(long)} to ensure that
     * subclass tables for the queued event are deleted.
     */
    @SqlUpdate("delete from queued_event where queued_event_id = :queuedEventId")
    int delete(@Bind("queuedEventId") long queuedEventId);

    /**
     * Deletes all queued events with the given id, including
     * all subclass tables.
     */
    default int deleteAllByQueuedEventId(long queuedEventId) {
        getHandle().attach(JdbiQueuedNotificationTemplateSubstitution.class).deleteByQueuedEventId(queuedEventId);
        getHandle().attach(JdbiQueuedNotification.class).delete(queuedEventId);
        return delete(queuedEventId);
    }

    @SqlUpdate("update queued_event set status_type_id = (select et.status_type_id from queued_event_status_type et "
            + "where et.status_type_code = 'PENDING') where queued_event_id = :queuedEventId")
    int markPending(@Bind("queuedEventId") long queuedEventId);

    @SqlUpdate("update queued_event set status_type_id = null where queued_event_id = :queuedEventId")
    int clearStatus(@Bind("queuedEventId") long queuedEventId);

    @SqlQuery("select queued_event_id from queued_event where event_configuration_id = :eventConfigurationId")
    List<Long> findQueuedEventIdsByEventConfigurationId(@Bind("eventConfigurationId") long eventConfigurationId);

    /**
     * Deletes all queued events for the given event configuration id
     * @param eventConfigurationId the event configuration id
     * @return number of rows deleted
     */
    @SqlUpdate("delete from queued_event where event_configuration_id = :eventConfigId")
    int deleteAllByEventConfigurationId(@Bind("eventConfigId") long eventConfigurationId);

    @SqlUpdate("delete qnts from queued_event as q"
            + "   left join queued_notification_template_substitution as qnts on qnts.queued_event_id = q.queued_event_id"
            + "  where q.event_configuration_id in ("
            + "        select e.event_configuration_id from event_configuration as e where e.umbrella_study_id = :studyId)")
    int deleteQueuedNotificationSubstitutionsByStudyId(@Bind("studyId") long studyId);

    @SqlUpdate("delete qnts from queued_event as q"
            + "   left join queued_notification_template_substitution as qnts on qnts.queued_event_id = q.queued_event_id"
            + "  where q.operator_user_id in (<userIds>) or q.participant_user_id in (<userIds>)")
    int deleteQueuedNotificationSubstitutionsByUserIds(@BindList(value = "userIds", onEmpty = EmptyHandling.NULL) Set<Long> userIds);

    @SqlUpdate("delete qn from queued_event as q"
            + "   left join queued_notification as qn on qn.queued_event_id = q.queued_event_id"
            + "  where q.event_configuration_id in ("
            + "        select e.event_configuration_id from event_configuration as e where e.umbrella_study_id = :studyId)")
    int deleteQueuedNotificationsByStudyId(@Bind("studyId") long studyId);

    @SqlUpdate("delete qn from queued_event as q"
            + "   left join queued_notification as qn on qn.queued_event_id = q.queued_event_id"
            + "  where q.operator_user_id in (<userIds>) or q.participant_user_id in (<userIds>)")
    int deleteQueuedNotificationsByUserIds(@BindList(value = "userIds", onEmpty = EmptyHandling.NULL) Set<Long> userIds);

    @SqlUpdate("delete from queued_event"
            + "  where event_configuration_id in ("
            + "        select e.event_configuration_id from event_configuration as e where e.umbrella_study_id = :studyId)")
    int deleteQueuedEventsByStudyId(@Bind("studyId") long studyId);

    @SqlUpdate("delete from queued_event where operator_user_id in (<userIds>) or participant_user_id in (<userIds>)")
    int deleteQueuedEventsByUserIds(@BindList(value = "userIds", onEmpty = EmptyHandling.NULL) Set<Long> userIds);
}
