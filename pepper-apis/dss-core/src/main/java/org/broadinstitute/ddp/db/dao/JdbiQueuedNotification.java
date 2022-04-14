package org.broadinstitute.ddp.db.dao;

import java.util.Map;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiQueuedNotification extends SqlObject {

    @SqlUpdate("insert into queued_notification(queued_event_id) values(:queuedEventId)")
    boolean insert(@Bind("queuedEventId") long queuedEventId);

    @SqlUpdate("delete from queued_notification where queued_event_id = :queuedEventId")
    int delete(@Bind("queuedEventId") long queuedEventId);

    /**
     * Updates the email address to use for this notification.  Do not
     * use this method when sending to users who have an auth0 account.
     * Instead use {@link QueuedEventDao#insertNotification(long, Long, Long, Long, Map)}.
     * @param queuedEventId the qeueued event id record to update
     * @param toEmailAddress the email address
     * @return number of rows updated
     */
    @SqlUpdate("update queued_notification set email_address = :email where queued_event_id = :queuedEventId")
    int updateEmailAddress(@Bind("queuedEventId") long queuedEventId,
                           @Bind("email") String toEmailAddress);
}
