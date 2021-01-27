package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.constants.SendGridEventTestConstants.SENDGRID_EVENT_TESTDATA;
import static org.broadinstitute.ddp.util.TestUtil.readJsonArrayFromFile;
import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;


import org.junit.Test;

public class SendGridEventServiceTest {

    final SendGridEventService sendGridEventService = new SendGridEventService();

    @Test
    public void testAuth0LogEventsParse() throws FileNotFoundException {
        var sendGridEvents = sendGridEventService.parseSendGridEvents(readJsonArrayFromFile(SENDGRID_EVENT_TESTDATA).toString());

        assertEquals(12, sendGridEvents.length);

        var sendGridEvent = sendGridEvents[0];
        assertEquals("example1@datadonationplatform.org", sendGridEvent.getEmail());
        assertEquals("2017-12-15T00:59:29Z", sendGridEvent.getTimestamp().toString());
        assertEquals("processed", sendGridEvent.getEventType());
        assertEquals("rbtnWrG1DVDGGGFHFyun0A==", sendGridEvent.getSgEventId());

        sendGridEvent = sendGridEvents[1];
        assertEquals("example2@datadonationplatform.org", sendGridEvent.getEmail());
        assertEquals("2017-12-16T04:46:09Z", sendGridEvent.getTimestamp().toString());
        assertEquals("dropped", sendGridEvent.getEventType());
        assertEquals("zmzJhfJgAfUSOW80yEbPyw==", sendGridEvent.getSgEventId());
    }
}
