package org.broadinstitute.ddp.tests.basiltests;

import static org.broadinstitute.ddp.BasilAppSite.auth0Page;
import static org.broadinstitute.ddp.BasilAppSite.consentPage;
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
import org.broadinstitute.ddp.tests.annotations.Hybrid;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisplayName("002 - Log in an existing user")
public class LoginExistingUserTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(LoginExistingUserTest.class);

    //Given an existing, logged out user opens https://basil-dev.datadonationplatform.org/basil-app/ in browser
    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        BasilAppWebsite.setDomain();
        return BasilAppSite.class;
    }

    @Before
    @Override
    public void background() {
        homePage.clickLogin();
    }

    @Test
    @Category({FeatureTests.class})
    @DisplayName("002.01 - Log in using app-credentials (customer username and password)")
    public void testExistingUserApplicationLogin() {
        String testUser = CONFIG.getString(ConfigFile.TEST_USER);
        String testUserPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);

        //Given user has entered “<email login>” into email text box
        //And user has entered "<password>" into password text box
        auth0Page.inputApplicationUserName(testUser);
        auth0Page.inputApplicationUserPassword(testUserPassword);

        //When user clicks "login" button
        auth0Page.clickApplicationSubmit();
        DatabaseUtility.verifyUserExists();

        //Then user should be redirected to the user dashboard or the last activity page they were on
        consentPage.verifyPageIsOpened();

        //End test
        endTest();
    }

    @Test
    @Ignore
    @Hybrid
    @Category({FeatureTests.class})
    @DisplayName("002.02 - Log in using Google credentials")
    public void testExistingUserGoogleLogin() {
        String socialMediaUser = CONFIG.getString(ConfigFile.SOCIAL_MEDIA_USER);
        String socialMediaUserPassword = CONFIG.getString(ConfigFile.SOCIAL_MEDIA_USER_PASSWORD);

        //Given user has clicked “log in with Google”
        auth0Page.clickLoginWithGoogle();

        //When user has entered "<google account>" in the email text box **manually entered to avoid google challenge
        //And user has clicked "submit" button **manually pressed to avoid google challenge
        auth0Page.inputGoogleUserName(socialMediaUser);

        //And user has entered "<password>" in the password text box **manually entered to avoid google challenge
        //And user has clicked "submit" button **manually pressed to avoid google challenge
        auth0Page.inputGoogleUserPassword(socialMediaUserPassword);

        //Then user should be redirected to the URL https://basil-dev.datadonationplatform.org/basil-app/prequalifier
        prequalifierPage.verifyOpened();

        //End test
        endTest();
    }

    @Test
    @Ignore
    @Category({FeatureTests.class})
    @DisplayName("002.03 - Log in using facebook credentials")
    public void testExistingUserFacebookLogin() {
        String socialMediaUser = CONFIG.getString(ConfigFile.SOCIAL_MEDIA_USER);
        String socialMediaUserPassword = CONFIG.getString(ConfigFile.SOCIAL_MEDIA_USER_PASSWORD);

        //Given user has clicked “log in with Facebook”
        auth0Page.clickLoginWithFacebook();

        //And user has been redirected to Facebook
        verifyCurrentUrlContainsString("facebook");

        //And user can see “continue as <user>” button ** in tests, you must login with credentials
        auth0Page.inputFacebookUserName(socialMediaUser);
        auth0Page.inputFacebookUserPassword(socialMediaUserPassword);

        //When user clicks "continue as <user>" **in tests, facebook's login button
        auth0Page.clickFacebookSubmit();

        //Then user should be redirected to the URL https://basil-dev.datadonationplatform.org/basil-app/prequalifier
        prequalifierPage.verifyOpened();

        //End test
        endTest();
    }

    @Override
    public void endTest() {
        consentPage.clickBasilHeaderLogout();
    }
}
