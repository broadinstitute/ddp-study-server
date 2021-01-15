package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.CLIENT_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.EMAIL;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.IP;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.TYPE;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.USER_AGENT;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.USER_ID;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.USER_NAME;
import static org.broadinstitute.ddp.json.auth0.Auth0LogEventNode.resolveStringValue;
import static org.broadinstitute.ddp.util.TestUtil.readJSONFromFile;
import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;


import org.junit.Test;


public class Auth0LogEventServiceTest {

    private static final String AUTH0_LOG_EVENTS_PAYLOAD_TESTDATA_FOLDER = "src/test/resources/auth0-log-event-testdata/";
    private static final String TESTDATA1 = AUTH0_LOG_EVENTS_PAYLOAD_TESTDATA_FOLDER + "auth0-log-event-testdata1.json";
    private static final String TESTDATA2 = AUTH0_LOG_EVENTS_PAYLOAD_TESTDATA_FOLDER + "auth0-log-event-testdata2.json";

    final Auth0LogEventService auth0LogEventService = new Auth0LogEventService();

    @Test
    public void testAuth0LogEventsParse() throws FileNotFoundException {
        final var logEvents = auth0LogEventService.parseAuth0LogEvents(readJSONFromFile(TESTDATA1).toString());

        assertEquals(2, logEvents.size());

        final var logEvent = logEvents.get(0);

        assertEquals("s", logEvent.get(TYPE.nodeName()).getAsString());
        assertEquals("auth0|XXX_user_id_XXX", logEvent.get(USER_ID.nodeName()).getAsString());
        assertEquals("XXX_client_id_XXX", logEvent.get(CLIENT_ID.nodeName()).getAsString());
        assertEquals("test_name@datadonationplatform.org", logEvent.get(USER_NAME.nodeName()).getAsString());

        assertEquals("test_name@datadonationplatform.org", resolveStringValue(EMAIL, logEvent));
    }

    @Test
    public void testOneAuth0LogEventParse() throws FileNotFoundException {
        final var logEvents = auth0LogEventService.parseAuth0LogEvents(readJSONFromFile(TESTDATA2).toString());

        assertEquals(1, logEvents.size());

        final var logEvent = logEvents.get(0);

        assertEquals("seccft", logEvent.get(TYPE.nodeName()).getAsString());
        assertEquals("XXX_client_id_XXX", logEvent.get(CLIENT_ID.nodeName()).getAsString());
        assertEquals("okhttp/3.7.0", resolveStringValue(USER_AGENT, logEvent));
        assertEquals("107.178.2.3", resolveStringValue(IP, logEvent));
    }
}
