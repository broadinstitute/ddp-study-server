package org.broadinstitute.ddp.tests;

import org.broadinstitute.ddp.SlackNotificationRunner;
import org.broadinstitute.ddp.tests.sandboxtests.LoginSandboxAutoTest;
import org.broadinstitute.ddp.tests.sandboxtests.UserProfileSandboxAutoTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(SlackNotificationRunner.class)
@Suite.SuiteClasses({
        LoginSandboxAutoTest.class,
        UserProfileSandboxAutoTest.class
})
public class SandboxTestSuite {

}
