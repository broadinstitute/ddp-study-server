package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.notficationevent.DsmNotificationEventType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
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
            @Bind("dsmEventType") DsmNotificationEventType dsmEventType);

    @SqlUpdate("insert into user_status_changed_trigger (event_trigger_id, target_status_type_id)"
            + " select :triggerId, enrollment_status_type_id"
            + "   from enrollment_status_type"
            + "  where enrollment_status_type_code = :targetStatus")
    int insertUserStatusChangedTrigger(
            @Bind("triggerId") long triggerId,
            @Bind("targetStatus") EnrollmentStatusType targetStatusType);
}
