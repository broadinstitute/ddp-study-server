package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.CLIENT_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.CONNECTION_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.DATA;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.DATE;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.EMAIL;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.IP;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.LOG_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.TYPE;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.USER_AGENT;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.USER_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.resolveDateTimeValue;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.resolveStringValue;

import java.util.Map;


import com.google.gson.JsonElement;
import org.jdbi.v3.sqlobject.CreateSqlObject;

public interface Auth0LogEventDao {

    @CreateSqlObject
    JdbiAuth0LogEvent getJdbiAuth0LogEvent();

    default long insertAuth0LogEvent(final Map<String, JsonElement> logEvent, String tenant) {
        return getJdbiAuth0LogEvent().insertAuth0LogEvent(
                tenant,
                resolveStringValue(LOG_ID, logEvent),
                resolveDateTimeValue(DATE, logEvent),
                resolveStringValue(TYPE, logEvent),
                resolveStringValue(CLIENT_ID, logEvent),
                resolveStringValue(CONNECTION_ID, logEvent),
                resolveStringValue(USER_ID, logEvent),
                resolveStringValue(USER_AGENT, logEvent),
                resolveStringValue(IP, logEvent),
                resolveStringValue(EMAIL, logEvent),
                resolveStringValue(DATA, logEvent)
        );
    }
}
