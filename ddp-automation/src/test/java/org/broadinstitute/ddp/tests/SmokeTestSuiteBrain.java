package org.broadinstitute.ddp.tests;

import org.broadinstitute.ddp.SlackNotificationRunner;
import org.broadinstitute.ddp.tests.braintests.BrainAppAutoTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(SlackNotificationRunner.class)
@Suite.SuiteClasses({
        BrainAppAutoTest.class
})
public class SmokeTestSuiteBrain {
}
