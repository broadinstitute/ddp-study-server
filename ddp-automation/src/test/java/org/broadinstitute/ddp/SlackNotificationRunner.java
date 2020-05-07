package org.broadinstitute.ddp;

import java.util.regex.Pattern;

import com.github.seratch.jslack.Slack;
import com.github.seratch.jslack.api.webhook.Payload;
import com.github.seratch.jslack.api.webhook.WebhookResponse;
import org.broadinstitute.ddp.tests.BaseTest;
import org.junit.experimental.categories.Categories;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SlackNotificationRunner extends Categories {

    private static final Logger logger = LoggerFactory.getLogger(SlackNotificationRunner.class);

    public static final String POST_TO_SLACK = "org.datadonationplatform.postToSlack";

    private static final boolean POST_SUMMARY_TO_SLACK = Boolean.getBoolean(POST_TO_SLACK);

    private static String SLACK_HOOK_URL = DDPWebSite.CONFIG.getString(ConfigFile.SLACK_HOOK_URL);


    public SlackNotificationRunner(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        super(klass, builder);
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
        notifier.addListener(new RunListener() {

            public void testRunFinished(Result result) throws Exception {
                if (POST_SUMMARY_TO_SLACK) {
                    logger.info("Posting summary info to slack");
                    String testSuiteLocation = SlackNotificationRunner.this.getDescription().getDisplayName();
                    String[] packages = testSuiteLocation.split(Pattern.quote("."));
                    int amountOfPackages = packages.length - 1;
                    String testSuite = packages[amountOfPackages] + "\n";
                    logger.info("Test Suite: {}", testSuite);

                    String browserStackEnvironment = "`" + BaseTest.browserStackEnvironments.toJSONString() + "`";
                    String envInfo = " on build *" + BaseTest.buildName + "* using " + browserStackEnvironment;
                    String moreDetails = " More details at "
                            + BaseTest.browserStackUtil.getBrowserStackAutomateReviewUrl(BaseTest.buildName);
                    Payload.PayloadBuilder slackBuilder = Payload.builder().channel("#pepper-ci");

                    if (result.getFailureCount() == 0) {
                        slackBuilder = slackBuilder.iconEmoji(":smiley:");
                        slackBuilder = slackBuilder.text(testSuite + "All " + result.getRunCount()
                                + " tests have passed" + envInfo + moreDetails);
                    } else {
                        slackBuilder = slackBuilder.iconEmoji(":scream:");
                        slackBuilder = slackBuilder.text(testSuite + result.getFailureCount() + " of " + result.getRunCount()
                                + " have failed" + envInfo + moreDetails);
                    }
                    Payload slackPayload = slackBuilder.build();

                    Slack slack = Slack.getInstance();
                    WebhookResponse response = slack.send(SLACK_HOOK_URL, slackPayload);

                    if (response.getCode() != 200) {
                        throw new RuntimeException("Could not post results to slack: "
                                + response.getCode() + ":" + response.getBody());
                    }
                } else {
                    logger.info("Skipping slack summary");
                }
            }
        });
    }
}
