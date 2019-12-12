package org.broadinstitute.ddp.tests;

import org.broadinstitute.ddp.SlackNotificationRunner;
import org.broadinstitute.ddp.tests.basiltests.BasilAppAutoTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(SlackNotificationRunner.class)
@Suite.SuiteClasses({
        BasilAppAutoTest.class})
public class SmokeTestSuiteBasilApp {

}
