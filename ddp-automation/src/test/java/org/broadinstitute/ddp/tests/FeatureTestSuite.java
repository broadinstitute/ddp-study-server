package org.broadinstitute.ddp.tests;

import org.broadinstitute.ddp.SlackNotificationRunner;
import org.broadinstitute.ddp.tests.basiltests.LoginExistingUserTest;
import org.broadinstitute.ddp.tests.basiltests.RegisterNewUserTest;
import org.broadinstitute.ddp.tests.basiltests.SubmitConsentFormTest;
import org.broadinstitute.ddp.tests.basiltests.SubmitPrequalifierTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(SlackNotificationRunner.class)
@Suite.SuiteClasses({
        RegisterNewUserTest.class,
        LoginExistingUserTest.class,
        SubmitPrequalifierTest.class,
        SubmitConsentFormTest.class
})
public class FeatureTestSuite {
}
