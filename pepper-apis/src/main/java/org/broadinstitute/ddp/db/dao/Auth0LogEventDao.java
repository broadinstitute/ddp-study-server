package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.json.auth0.Auth0LogEvent;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface Auth0LogEventDao extends SqlObject {

    @CreateSqlObject
    JdbiAuth0LogEvent getJdbiAuth0LogEvent();

    /**
     * Insert Auth0 log event.
     * Fetch event type details.
     */
    default void insertAuth0LogEvent(Auth0LogEvent logEvent) {
        getJdbiAuth0LogEvent().insertAuth0LogEvent(
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
        var auth0LogEventCodeDto = getJdbiAuth0LogEvent().findAuth0LogEventCodeByType(logEvent.getType());
        if (auth0LogEventCodeDto.isPresent()) {
            logEvent.setAuth0LogEventCode(auth0LogEventCodeDto.get());
        }
    }

    /**
     * Check if auth0_log_event with same log_id+tenant already exist in DB
     * @return boolean true, if already exist, otherwise false
     */
    default boolean checkIfSameEventAlreadyPersisted(Auth0LogEvent logEvent) {
        return getJdbiAuth0LogEvent().findAuth0LogIdByTenantAndLogId(logEvent.getTenant(), logEvent.getLogId()) != null;
    }
}
