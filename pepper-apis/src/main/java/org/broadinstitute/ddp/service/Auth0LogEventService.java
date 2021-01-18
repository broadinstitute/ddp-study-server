package org.broadinstitute.ddp.service;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.CLIENT_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.CONNECTION_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.DATA;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.DATE;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.EMAIL;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.IP;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.LOGS;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.LOG_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.TYPE;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.USER_AGENT;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.USER_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.USER_NAME;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.broadinstitute.ddp.db.dao.Auth0LogEventDao;
import org.broadinstitute.ddp.json.auth0.Auth0LogEvent;
import org.broadinstitute.ddp.util.GsonRecursiveReader;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;

/**
 * The service used for handling Auth0 log events which fired by Auth0 streams
 * (see https://manage.auth0.com/dashboard/us/ddp-dev/log-streams).<br>
 * <b>The service provides the following functions:</b>
 * <ul>
 *     <li>parses JSON doc (coming as a payload in POST request specified in Auth0 Custom Webhook)
 *     extracting log events data from it;</li>
 *     <li>saves extracted log events to DB table 'auth0_log_event';</li>
 *     <li>adds extracted log events to log (together with tenant name detected from POST URL).</li>
 * </ul>
 * NOTE: see Auth0LogEventServiceTest for examples of Auth0 log events JSON docs.
 */
public class Auth0LogEventService {

    private static final Logger LOG = getLogger(Auth0LogEventService.class);

    /**
     * List of Auth0 Log Event JSON node names which to read (recursively)
     */
    static final List<String> AUTH0_LOG_EVENT_NODE_NAMES = List.of(
            LOG_ID.nodeName(),
            DATE.nodeName(),
            TYPE.nodeName(),
            CLIENT_ID.nodeName(),
            CONNECTION_ID.nodeName(),
            USER_ID.nodeName(),
            USER_AGENT.nodeName(),
            IP.nodeName(),
            EMAIL.nodeName(),
            USER_NAME.nodeName(),
            DATA.nodeName()
    );

    /**
     * Parses JSON doc (coming as a payload in POST request specified in Auth0 Custom Webhook)
     * extracting log events data from it. Data fetched according to names
     * defined in {@link #AUTH0_LOG_EVENT_NODE_NAMES}.
     *
     * @param logEventsJson payload doc coming with POST request (from Auth0 stream).
     * @return {@code List<Map<String, JsonElement>>} list of maps with parsed log events data
     */
    public List<Map<String, JsonElement>> parseAuth0LogEvents(String logEventsJson) {
        var rootNode = new Gson().fromJson(logEventsJson, JsonObject.class);
        var logsNode = rootNode.getAsJsonArray(LOGS.nodeName());
        final List<Map<String, JsonElement>> logEvents = new ArrayList<>();
        for (final var node : logsNode) {
            logEvents.add(GsonRecursiveReader.readValues(node, AUTH0_LOG_EVENT_NODE_NAMES));
        }
        return logEvents;
    }

    public void logAuth0LogEvent(final Auth0LogEvent logEvent) {
        if (LOG.isDebugEnabled() || LOG.isTraceEnabled()) {
            LOG.debug(
                    "AUTH0-LOG-EVENT[{}]: type:{} ({}), date:{}, log_id:{}\n"
                            + "\tclient_id:{}, connection_id:{}, user_id:{}\n"
                            + "\tuser_agent:{}\n"
                            + "\tip:{}, email:{}\n"
                            + "\tdata: {}",
                    logEvent.getTenant(),
                    logEvent.getType(),
                    getTypeDescription(logEvent),
                    logEvent.getDate(),
                    logEvent.getLogId(),
                    logEvent.getClientId(),
                    logEvent.getConnectionId(),
                    logEvent.getUserId(),
                    logEvent.getUserAgent(),
                    logEvent.getIp(),
                    logEvent.getEmail(),
                    logEvent.getData());
        } else {
            LOG.info("AUTH0-LOG-EVENT[{}]: type:{} ({}), date:{}, user_id:{}, log_id:{}",
                    logEvent.getTenant(),
                    logEvent.getType(),
                    getTypeDescription(logEvent),
                    logEvent.getDate(),
                    logEvent.getUserId(),
                    logEvent.getLogId());
        }
    }

    /**
     * Save log event and tenant data to DB table 'auth0-log-event'
     */
    public void persistAuth0LogEvent(final Handle handle, final Auth0LogEvent logEvent) {
        final var auth0LogEventDao = handle.attach(Auth0LogEventDao.class);
        auth0LogEventDao.insertAuth0LogEvent(logEvent);
    }

    private String getTypeDescription(final Auth0LogEvent logEvent) {
        if (logEvent.getAuth0LogEventCode() != null) {
            return isNotBlank(logEvent.getAuth0LogEventCode().getDescription())
                    ? logEvent.getAuth0LogEventCode().getDescription() : logEvent.getAuth0LogEventCode().getTitle();
        }
        return null;
    }
}
