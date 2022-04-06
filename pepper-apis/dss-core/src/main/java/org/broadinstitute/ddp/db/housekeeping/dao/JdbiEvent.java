package org.broadinstitute.ddp.db.housekeeping.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiEvent extends SqlObject {

    @SqlQuery("select 1 from event where ((max_occurrences is not null and num_occurrences_processed < max_occurrences)"
            + " or max_occurrences is null) and event_guid = :eventGuid and event_type_id = (select et.event_type_id "
            + "from event_type et where et.event_type_code = :eventType) FOR UPDATE")
    Optional<Integer> lockIfShouldHandle(@Bind("eventGuid") String eventGuid, @Bind("eventType") String eventType);

    @SqlUpdate("insert into event(event_guid,event_type_id,max_occurrences) "
            + "(select :eventGuid,et.event_type_id,:ignoreAfter from event_type et where et.event_type_code = "
            + ":eventType) ON DUPLICATE KEY UPDATE max_occurrences = :ignoreAfter")
    int upsertEvent(@Bind("eventGuid") String eventGuid,
                    @Bind("eventType") String eventType,
                    @Bind("ignoreAfter") Integer ignoreAfter);

    @SqlQuery("select event_id from event where event_guid = :eventGuid FOR UPDATE")
    Optional<Long> findByGuidWithLock(@Bind("eventGuid") String eventGuid);

    @SqlUpdate("insert into event(event_guid,event_type_id,max_occurrences) "
            + "(select :eventGuid,et.event_type_id,:ignoreAfter from event_type et where et.event_type_code = "
            + ":eventType)")
    @GetGeneratedKeys
    long insert(@Bind("eventGuid") String eventGuid,
                @Bind("eventType") String eventType,
                @Bind("ignoreAfter") Integer ignoreAfter);

    @SqlUpdate("update event set max_occurrences = :ignoreAfter where event_guid = :eventGuid")
    int updateIgnoreAfter(@Bind("eventGuid") String eventGuid, @Bind("ignoreAfter") Integer ignoreAfter);

    default boolean shouldHandleEvent(String eventGuid, String eventType, Integer ignoreAfter) {
        if (!findByGuidWithLock(eventGuid).isPresent()) {
            long eventId = insert(eventGuid, eventType, ignoreAfter);
        } else {
            int numRowsUpdated = updateIgnoreAfter(eventGuid, ignoreAfter);
            if (numRowsUpdated != 1) {
                throw new DaoException("Updated " + numRowsUpdated + " rows for event guid " + eventGuid);
            }
        }
        return lockIfShouldHandle(eventGuid, eventType).isPresent();
    }

    @SqlUpdate("update event set num_occurrences_processed = num_occurrences_processed + 1 "
            + "where event_guid = :eventGuid")
    boolean incrementOccurrencesProcessed(@Bind("eventGuid") String eventGuid);

}
