package org.broadinstitute.ddp;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.LoggingEvent;
import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.webhook.Payload;
import com.github.seratch.jslack.api.webhook.WebhookResponse;
import org.broadinstitute.ddp.log.SlackAppender;
import org.junit.Before;
import org.junit.Test;

public class SlackAppenderTest {

    private LoggingEvent loggingEvent = new LoggingEvent();

    @Before
    public void setUp() {
        loggingEvent.setMessage("Hi there");
        loggingEvent.setCallerData(new StackTraceElement[] {new StackTraceElement("Foo",
                "bar",
                "Foo.java",
                0)});
    }

    @Test
    public void testSuccessfulLoggingToSlack() throws Exception {
        Slack mockSlack = mock(Slack.class);
        WebhookResponse webhookResponse = WebhookResponse.builder().code(200).build();
        when(mockSlack.send(anyString(), any(Payload.class))).thenReturn(webhookResponse);

        SlackAppender slackAppender = new SlackAppender(mockSlack, "fakeHook", "fakeChannel");
        slackAppender.start();
        slackAppender.doAppend(loggingEvent);

        verify(mockSlack, times(1)).send(anyString(), any(Payload.class));
    }
}
