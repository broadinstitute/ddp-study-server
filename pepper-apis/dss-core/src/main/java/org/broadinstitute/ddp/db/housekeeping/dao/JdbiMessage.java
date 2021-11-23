package org.broadinstitute.ddp.db.housekeeping.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiMessage extends SqlObject {

    @SqlUpdate("insert into message(pubsub_message_id,is_processed) values(:messageId,false)")
    @GetGeneratedKeys
    long insertMessage(@Bind("messageId") String pubSubMessageId);

    @SqlUpdate("insert into message(is_processed,event_id) (select false,e.event_id from event e "
            + "where e.event_guid = :eventGuid)")
    @GetGeneratedKeys
    long insertMessageForEvent(@Bind("eventGuid") String eventGuid);

    /**
     * Locks the given message if it's unprocessed
     **/
    @SqlQuery("select 1 from message where ddp_message_id = :ddpMessageId and not is_processed FOR UPDATE")
    Optional<Integer> lockIfShouldProcess(@Bind("ddpMessageId") String ddpMessageId);

    @SqlUpdate("update message set is_processed = true where ddp_message_id = :ddpMessageId")
    int markMessageAsProcessed(@Bind("ddpMessageId") String ddpMessageId);

    /**
     * Pubsub is an "at least once" delivery system--therefore it may send the same message multiple times.  This
     * method exists to prevent such spurious duplicates by using database locking.
     *
     * <p>This is different from the event-based "ignoreAfter" number, which is used to
     * restrict how many occurrences of an event should be processed.
     * @param ddpEventId id of the event to check
     * @return
     */
    default boolean shouldProcessMessage(String ddpEventId) {
        Optional<Integer> lock = lockIfShouldProcess(ddpEventId);
        return lock.isPresent();
    }
}
