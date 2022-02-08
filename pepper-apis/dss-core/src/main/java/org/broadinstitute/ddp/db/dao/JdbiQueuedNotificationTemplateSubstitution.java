package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiQueuedNotificationTemplateSubstitution extends SqlObject {

    @SqlUpdate("insert into queued_notification_template_substitution(queued_event_id, variable_name, value) "
            + " values(:queuedEventId,:variableName,:value)")
    @GetGeneratedKeys
    long insert(@Bind("queuedEventId") long queuedNotificationId,
                @Bind("variableName") String variableName,
                @Bind("value") String value);

    @SqlUpdate("delete from queued_notification_template_substitution where queued_event_id = :queuedEventId")
    int deleteByQueuedEventId(@Bind("queuedEventId") long queuedEventId);
}
