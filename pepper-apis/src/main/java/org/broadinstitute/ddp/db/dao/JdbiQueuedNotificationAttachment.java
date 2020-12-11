package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiQueuedNotificationAttachment extends SqlObject {

    @SqlUpdate("insert into notification_attachment(queued_event_id, file_upload_id) "
            + " values(:queuedEventId,:fileUploadId)")
    @GetGeneratedKeys
    long insert(@Bind("queuedEventId") long queuedNotificationId,
                @Bind("fileUploadId") long fileUploadId);

    @SqlUpdate("delete from notification_attachment where queued_event_id = :queuedEventId")
    int deleteByQueuedEventId(@Bind("queuedEventId") long queuedEventId);
}
