package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiEventTrigger extends SqlObject {
    @SqlUpdate("insert into event_trigger (event_trigger_type_id) values ("
            + "(select event_trigger_type_id from event_trigger_type where event_trigger_type_code = :triggerType))")
    @GetGeneratedKeys
    long insert(@Bind("triggerType") EventTriggerType triggerType);

    @SqlUpdate("delete from event_trigger where event_trigger_id = :eventTriggerId")
    int deleteById(@Bind("eventTriggerId") long eventTriggerId);

}
