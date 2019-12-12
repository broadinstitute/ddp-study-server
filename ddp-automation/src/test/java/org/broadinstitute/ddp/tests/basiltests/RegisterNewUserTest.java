package org.broadinstitute.ddp.tests.basiltests;

import static org.broadinstitute.ddp.BasilAppSite.auth0Page;
import static org.broadinstitute.ddp.BasilAppSite.homePage;
import static org.broadinstitute.ddp.BasilAppSite.prequalifierPage;
import static org.broadinstitute.ddp.DDPWebSite.CONFIG;

import org.broadinstitute.ddp.BasilAppSite;
import org.broadinstitute.ddp.BasilAppWebsite;
import org.broadinstitute.ddp.ConfigFile;
import org.broadinstitute.ddp.DDPWebSite;
import org.broadinstitute.ddp.tests.BaseTest;
import org.broadinstitute.ddp.tests.DatabaseUtility;
import org.broadinstitute.ddp.tests.FeatureTests;
import org.broadinstitute.ddp.util.JDITestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterNewUserTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RegisterNewUserTest.class);
    private String newUser;
    private String newUserPassword;

    // Given a new user opens URL https://basil-dev.datadonationplatform.org/ in browser
    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        BasilAppWebsite.setDomain();
        return BasilAppSite.class;
    }

    @Before
    @Override
    public void background() {
        //And “login” button is visible
        //When user clicks on “login”
        homePage.clickLogin();
        newUser = JDITestUtils.generateNewUserEmail();
        newUserPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);

        //And user clicks on “sign up” tab
        auth0Page.clickSignUpNewUser();
    }

    @Test
    @Category({FeatureTests.class})
    @DisplayName("001.01 - Register using app-specific credentials (customer username and password)")
    public void testNewUserApplicationSignup() {
        //Given user enters "<username>"
        auth0Page.inputApplicationUserName(newUser);

        //And user enters "<password>"
        auth0Page.inputApplicationUserPassword(newUserPassword);

        //When user clicks "sign up" button
        auth0Page.clickApplicationSubmit();
        DatabaseUtility.verifyUserExists();

        //Then user should be redirected to https://basil-dev.datadonationplatform.org/prequalifier
        prequalifierPage.verifyPageIsOpened();

        //End test
        endTest();
    }

    @Override
    public void endTest() {
        prequalifierPage.clickBasilHeaderLogout();
    }
}
