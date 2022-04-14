package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiEventAction extends SqlObject {
    @SqlUpdate("insert into event_action (message_destination_id, event_action_type_id) values (:messageDestinationId,"
            + " (select event_action_type_id from event_action_type where event_action_type_code = :actionType))")
    @GetGeneratedKeys
    long insert(@Bind("messageDestinationId") Long messageDestinationId, @Bind("actionType") EventActionType actionType);

    @SqlUpdate("delete from event_action where event_action_id = :eventActionId")
    int deleteById(@Bind("eventActionId") long eventActionId);
}
