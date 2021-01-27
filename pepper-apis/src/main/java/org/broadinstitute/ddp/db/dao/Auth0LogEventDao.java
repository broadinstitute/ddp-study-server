package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.service.Auth0LogEventService.AUTH0_LOG_EVENT_TITLE;
import static org.slf4j.LoggerFactory.getLogger;


import org.broadinstitute.ddp.json.auth0.Auth0LogEvent;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;

public interface Auth0LogEventDao extends SqlObject {

    Logger LOG = getLogger(Auth0LogEventDao.class);

    @CreateSqlObject
    JdbiAuth0LogEvent getJdbiAuth0LogEvent();

    /**
     * Check if auth0 log event exist: if not then insert it, then find code details info and
     * save to {@link Auth0LogEvent} object (in order to use it in logging).<br>
     * If such event (with same log_id) already exist in DB then do nothing and
     * just log warn message.
     *
     * @param logEvent event which to persist
     * @return Long inserted auth0 log event ID (or null if insert not occured - because of such event already persisted before)
     */
    default Long insertAuth0LogEvent(Auth0LogEvent logEvent) {
        if (getJdbiAuth0LogEvent().findAuth0LogEventByLogId(logEvent.getLogId()) != null) {
            LOG.warn(AUTH0_LOG_EVENT_TITLE + " failed. Event with such log_id already was saved. LOG_ID=" + logEvent.getLogId());
            return null;
        } else {
            long id = getJdbiAuth0LogEvent().insertAuth0LogEvent(
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
            return id;
        }
    }
}
