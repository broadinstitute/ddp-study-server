package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiDsmNotificationTrigger extends SqlObject {
    @SqlUpdate("insert into dsm_notification_trigger (dsm_notification_trigger_id, dsm_notification_event_type_id)"
            + " values (:triggerId, :eventTypeId)"
    )
    int insert(
            @Bind("triggerId") long triggerId,
            @Bind("eventTypeId") long eventTypeId
    );
}
