package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.json.auth0.Auth0LogEvent;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface Auth0LogEventDao extends SqlObject {

    @CreateSqlObject
    JdbiAuth0LogEvent getJdbiAuth0LogEvent();

    default long insertAuth0LogEvent(final Auth0LogEvent logEvent) {
        return getJdbiAuth0LogEvent().insertAuth0LogEvent(
                logEvent.getTenant(),
                logEvent.getType(),
                logEvent.getDate(),
                logEvent.getLogId(),
                logEvent.getClientId(),
                logEvent.getConnectionId(),
                logEvent.getUserId(),
                logEvent.getUserAgent(),
                logEvent.getIp(),
                logEvent.getEmail(),
                logEvent.getData());
    }
}
