package org.broadinstitute.ddp.tests;

import org.broadinstitute.ddp.SlackNotificationRunner;
import org.broadinstitute.ddp.tests.angiotests.AngioAppAutoTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(SlackNotificationRunner.class)
@Suite.SuiteClasses({
        AngioAppAutoTest.class
})
public class SmokeTestSuiteAngio {
}
