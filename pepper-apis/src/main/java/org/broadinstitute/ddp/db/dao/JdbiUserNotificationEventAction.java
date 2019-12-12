package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUserNotificationEventAction extends SqlObject {

    @SqlUpdate("insert into user_notification_event_action"
            + "(user_notification_event_action_id,notification_type_id,notification_service_id,"
            + "notification_template_id,linked_activity_id) values(:actionId,:typeId,:serviceId,:templateId,:linkedActivityId)")
    int insert(@Bind("actionId") long eventActionId, @Bind("typeId") long notificationTypeId,
               @Bind("serviceId") long notificationServiceId, @Bind("templateId") long notificationTemplateId,
               @Bind("linkedActivityId") Long linkedActivityId);
}
