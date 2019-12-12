package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiNotificationType extends SqlObject {

    @SqlQuery("select notification_type_id from notification_type where notification_type_code = :notificationType")
    long findByType(@Bind("notificationType") NotificationType type);

}
