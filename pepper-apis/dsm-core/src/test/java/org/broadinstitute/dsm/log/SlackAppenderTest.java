//package org.broadinstitute.dsm.log;
//
//import static org.mockserver.model.HttpRequest.request;
//import static org.mockserver.model.HttpResponse.response;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//
//import com.google.gson.Gson;
//import com.typesafe.config.Config;
//import com.typesafe.config.ConfigValueFactory;
//import org.apache.log4j.Level;
//import org.apache.log4j.spi.LoggingEvent;
//import org.apache.log4j.spi.RootLogger;
//import org.broadinstitute.dsm.TestHelper;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Rule;
//import org.junit.Test;
//import org.mockserver.junit.MockServerRule;
//import org.mockserver.model.JsonBody;
//
//public class SlackAppenderTest extends TestHelper {
//
//
//    @Rule
//    public MockServerRule mockServerRule = new MockServerRule(this);
//    public Config cfg;
//    private LoggingEvent loggingEvent = new LoggingEvent(null, new RootLogger(Level.ERROR), Level.ERROR, null, new Throwable());
//
//    @Before
//    public void setUp() {
//        setupDB();
//        cfg = TestHelper.cfg;
//    }
//
//    @Test
//    public void testSuccessfulLoggingToSlack() throws Exception {
//
//        TestHelper.startMockServer();
//
//        if (mockDDP.isRunning()) {
//            mockDDP.when(request().withPath("/mock_slack_test"))
//                    .respond(response()
//                            .withStatusCode(200)
//                            .withBody("ok"));
//
//            SlackAppender slackAppender = new SlackAppender();
//
//            cfg = cfg.withValue("slack.hook", ConfigValueFactory.fromAnyRef("http://localhost:" + mockDDP.getPort() + "/mock_slack_test"));
//            cfg = cfg.withValue("slack.channel", ConfigValueFactory.fromAnyRef("SlackChannel"));
//
//            String appEnv = cfg.getString("portal.environment");
//            String slackHookUrlString = cfg.getString("slack.hook");
//            URI slackHookUrl;
//            String slackChannel = cfg.getString("slack.channel");
//            String gcpServiceName = cfg.getString("slack.gcpServiceName");
//            try {
//                slackHookUrl = new URI(slackHookUrlString);
//            } catch (URISyntaxException e) {
//                throw new IllegalArgumentException("Could not parse " + slackHookUrlString);
//            }
//
//            SlackAppender.configure(null, appEnv, slackHookUrl, slackChannel, gcpServiceName, TestHelper.class.getPackageName());
//
//            slackAppender.doAppend(loggingEvent);
//
//            String note = slackAppender.buildMessage(
//                    slackAppender.getErrorMessageAndLocation(loggingEvent),
//                    slackAppender.buildLinkToGcpError(loggingEvent),
//                    slackAppender.NON_JOB_ERROR_MESSAGE,
//                    slackAppender.buildLinkToGcpLog()
//            );
//
//            SlackAppender.SlackMessagePayload error_alert =
//                    slackAppender.buildSlackMessageWithPayload(note);
//
//            mockDDP.verify(request().withPath("/mock_slack_test").withBody(JsonBody.json(
//                    new Gson().toJson(error_alert))));
//        } else {
//            Assert.fail("Mock slack not running");
//        }
//    }
//
//}
