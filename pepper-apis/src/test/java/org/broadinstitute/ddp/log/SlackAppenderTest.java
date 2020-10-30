package org.broadinstitute.ddp.log;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonPathBody.jsonPath;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.*;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.matchers.Times;
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

            SlackAppender slackAppender = new SlackAppender("http://localhost:" + mockServerRule.getPort() + "/mock_slack_test" ,
                    "SlackChannel", 100, 10);
            slackAppender.start();
            slackAppender.doAppend(loggingEvent);

            slackAppender.waitForClearToQueue(3000);

            mockServerClient.verify(request().withPath("/mock_slack_test").withBody(JsonBody.json("{\n" +
                    "      \"text\" : \"*Hi there*\\n ``````\",\n" +
                    "      \"channel\" : \"SlackChannel\",\n" +
                    "      \"username\" : \"Pepper\",\n" +
                    "      \"icon_emoji\" : \":nerd_face:\", \"unfurl_links\":false" +
                    "    }")));
        } else {
            Assert.fail("Mock slack not running");
        }
    }

}
