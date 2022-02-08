package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiDsmNotificationEventType extends SqlObject {

    @SqlQuery(
            "select dsm_notification_event_type_id from dsm_notification_event_type"
                + " where dsm_notification_event_type_code = :code"
    )
    Optional<Long> findIdByCode(@Bind("code") String code);
}
