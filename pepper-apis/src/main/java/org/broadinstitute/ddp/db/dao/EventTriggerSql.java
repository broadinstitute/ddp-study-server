package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface EventTriggerSql extends SqlObject {

    @SqlUpdate("insert into event_trigger (event_trigger_type_id)"
            + " select event_trigger_type_id from event_trigger_type where event_trigger_type_code = :triggerType")
    @GetGeneratedKeys
    long insertBaseTrigger(@Bind("triggerType") EventTriggerType triggerType);

    @SqlUpdate("delete from event_trigger where event_trigger_id = :eventTriggerId")
    int deleteBaseTriggerById(@Bind("eventTriggerId") long eventTriggerId);

    @SqlUpdate("insert into dsm_notification_trigger (dsm_notification_trigger_id, dsm_notification_event_type_id)"
            + " select :triggerId, dsm_notification_event_type_id"
            + "   from dsm_notification_event_type"
            + "  where dsm_notification_event_type_code = :dsmEventType")
    int insertDsmNotificationTrigger(
            @Bind("triggerId") long triggerId,
            @Bind("dsmEventType")DsmNotificationEventType dsmEventType);

}
