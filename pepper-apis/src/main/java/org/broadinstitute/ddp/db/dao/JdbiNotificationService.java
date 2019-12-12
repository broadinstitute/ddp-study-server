package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiNotificationService extends SqlObject {

    @SqlQuery("select notification_service_id from notification_service where service_code = :serviceCode")
    long findByType(@Bind("serviceCode") NotificationServiceType serviceType);

}
