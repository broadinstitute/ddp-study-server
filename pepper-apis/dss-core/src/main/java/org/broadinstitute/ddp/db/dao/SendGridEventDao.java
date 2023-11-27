package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.json.sendgrid.SendGridEvent;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface SendGridEventDao extends SqlObject {

    @CreateSqlObject
    JdbiSendGridEvent getJdbiSendGridEvent();

    default long insertSendGridEvent(SendGridEvent sendGridEvent) {
        return getJdbiSendGridEvent().inserSendGridEvent(
                sendGridEvent.getEmail(),
                sendGridEvent.getTimestamp(),
                sendGridEvent.getEventType(),
                sendGridEvent.getUrl(),
                sendGridEvent.getIp(),
                sendGridEvent.getSgEventId(),
                sendGridEvent.getSgMessageId(),
                sendGridEvent.getResponse(),
                sendGridEvent.getReason(),
                sendGridEvent.getStatus(),
                sendGridEvent.getAttempt()
        );
    }
}
