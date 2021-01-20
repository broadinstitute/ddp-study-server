package org.broadinstitute.ddp.log;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import ch.qos.logback.classic.spi.LoggingEvent;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.JsonBody;

public class SlackAppenderTest {

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    private MockServerClient mockServerClient;

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
        mockServerClient = mockServerClient.reset();

        if (mockServerClient.hasStarted()) {
            mockServerClient.when(request().withPath("/mock_slack_test"))
                    .respond(response()
                            .withStatusCode(200)
                            .withBody("ok"));

            SlackAppender slackAppender = new SlackAppender("http://localhost:" + mockServerRule.getPort() + "/mock_slack_test",
                    "SlackChannel", 100, 10);
            slackAppender.start();
            slackAppender.doAppend(loggingEvent);

            slackAppender.waitForClearToQueue(3000);

            mockServerClient.verify(request().withPath("/mock_slack_test").withBody(JsonBody.json(
                    new Gson().toJson(new SlackAppender.SlackMessagePayload("[test-GAE-service] *Hi there*\n ``````",
                            "SlackChannel",
                            "Pepper",
                            ":nerd_face:")))));
        } else {
            Assert.fail("Mock slack not running");
        }
    }
}
