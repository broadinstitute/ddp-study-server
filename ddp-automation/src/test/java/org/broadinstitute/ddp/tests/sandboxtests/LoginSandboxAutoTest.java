package org.broadinstitute.ddp.tests.sandboxtests;

import static com.epam.jdi.uitests.web.settings.WebSettings.getDriver;
import static org.broadinstitute.ddp.DDPWebSite.CONFIG;
import static org.broadinstitute.ddp.earlypepper.SandboxAppSite.auth0Page;
import static org.broadinstitute.ddp.earlypepper.SandboxAppSite.loginPage;

import org.broadinstitute.ddp.ConfigFile;
import org.broadinstitute.ddp.DDPWebSite;
import org.broadinstitute.ddp.earlypepper.SandboxAppSite;
import org.broadinstitute.ddp.earlypepper.SandboxAppWebsite;
import org.broadinstitute.ddp.tests.BaseTest;
import org.broadinstitute.ddp.tests.DatabaseUtility;
import org.broadinstitute.ddp.tests.SandboxTests;
import org.broadinstitute.ddp.util.JDITestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginSandboxAutoTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(LoginSandboxAutoTest.class);
    private static final String LOGIN_LOGOUT_TEST_CAPTION = "AAA";
    private static final String LOGOUT_EVENT_TEST_CAPTION = "logout event occurs";
    private static final String NULL_TEST_CAPTION = "null";

    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        SandboxAppWebsite.setDomain();
        return SandboxAppSite.class;
    }

    @Before
    @Override
    public void background() {
        //Not needed here
    }

    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.001-01 - Default initial state. 1. Not logged in user")
    public void loginButtonShouldHaveDefaultInitialStateForNotLoggedInUser() {
        // Given user is <authenticationState> --> logged out
        // When user is navigated to "/login" page
        openPage(loginPage);

        // Then "<currentButton>" should be visible
        // And "<currentButton>" button should have "<caption>" caption
        // And "<backingButton>" should not be visible
        loginPage.verifyLoginButtonDisplayed();

        //End test
        endTest();

        // Examples:
        // | authenticationState | caption | currentButton | backingButton |
        // | logged out          | LOG OUT | Login         | Logout        |
    }

    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.001-01 - Default initial state. 2. Logged in user")
    public void loginButtonShouldHaveDefaultInitialStateForLoggedInUser() {
        // Given user is <authenticationState> --> logged in
        String user = CONFIG.getString(ConfigFile.TEST_USER);
        String password = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        SandboxAppSite.clickLogin();

        auth0Page.inputApplicationUserName(user);
        auth0Page.inputApplicationUserPassword(password);
        auth0Page.clickApplicationSubmit();
        logger.info("Current URL: {}", getDriver().getCurrentUrl());
        DatabaseUtility.verifyUserExists();

        // When user is navigated to "/login" page
        openPage(loginPage);

        //End test
        endTest();

        // Then "Logout" button should be visible
        // And "<Logout>" button should have "LOG OUT" caption
        // And "Login" button should not be visible
        loginPage.verifyLogoutButtonDisplayed();
    }

    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.001-02 - Change state on the fly")
    public void changeStateOnTheFly() {
        // Given user is <authenticationState> --> logged in
        String user = JDITestUtils.generateNewUserEmail();
        String password = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        SandboxAppSite.clickLogin();

        auth0Page.clickSignUpNewUser();
        auth0Page.inputApplicationUserName(user);
        auth0Page.inputApplicationUserPassword(password);
        auth0Page.clickApplicationSubmit();
        logger.info("Current URL: {}", getDriver().getCurrentUrl());
        DatabaseUtility.verifyUserExists();

        // And user navigates to "/login" page
        openPage(loginPage);

        // When user clicks on "LogOut" button
        loginPage.clickLogout();

        // Then "LogIn" should be visible
        // Then "LogIn" button should have "LOG IN" caption
        //And "LogOut" should not be visible
        loginPage.verifyLoginButtonDisplayed();

        //End test
        endTest();
    }

    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.001-03 - Change caption. 1. Not logged in user")
    public void changeCaptionForLoggedOutUser() {
        // Given user is --> logged out
        // And user navigates to "/login" page
        openPage(loginPage);

        // When user sets "AAA" into the text box
        loginPage.inputLoginCaption(LOGIN_LOGOUT_TEST_CAPTION);

        // Then "login" button should have "AAA" caption
        loginPage.verifyLoginCaption(LOGIN_LOGOUT_TEST_CAPTION);

        //End test
        endTest();
    }

    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.001-03 - Change caption. 1. Logged in user")
    public void changeCaptionForLoggedInUser() {
        // Given user is <authenticationState> --> logged in
        String user = JDITestUtils.generateNewUserEmail();
        String password = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        SandboxAppSite.clickLogin();

        auth0Page.clickSignUpNewUser();
        auth0Page.inputApplicationUserName(user);
        auth0Page.inputApplicationUserPassword(password);
        auth0Page.clickApplicationSubmit();
        logger.info("Current URL: {}", getDriver().getCurrentUrl());
        DatabaseUtility.verifyUserExists();

        // And user navigates to "/login" page
        openPage(loginPage);

        // When user sets "AAA" into <input> text box
        loginPage.logoutCaptionInput.clear();
        loginPage.logoutCaptionInput.input(LOGIN_LOGOUT_TEST_CAPTION);

        // Then "<currentButton>" button should have "AAA" caption
        loginPage.verifyLogoutCaption(LOGIN_LOGOUT_TEST_CAPTION);

        //End test
        endTest();
    }

    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.001-04 - Capture logout event")
    public void captureLogoutEvent() {
        // Given user is logged in
        String user = JDITestUtils.generateNewUserEmail();
        String password = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        SandboxAppSite.clickLogin();

        auth0Page.clickSignUpNewUser();
        auth0Page.inputApplicationUserName(user);
        auth0Page.inputApplicationUserPassword(password);
        auth0Page.clickApplicationSubmit();
        logger.info("Current URL: {}", getDriver().getCurrentUrl());
        DatabaseUtility.verifyUserExists();

        // And user navigates to "/login" page
        openPage(loginPage);

        // When user clicks on "Logout" button
        loginPage.logout.click();

        // Then user should be logged out
        loginPage.login.waitDisplayed();

        // And "logout event occurs" text block should be visible
        loginPage.logoutEvent.waitDisplayed();
        loginPage.logoutEvent.waitText(LOGOUT_EVENT_TEST_CAPTION);

        //End test
        endTest();
    }

    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.001-5 - Make server call for logged out user")
    public void makeServerCallForLoggedOutUser() {
        // Given user is logged out
        // And user navigates to "/login" page
        openPage(loginPage);

        // When user clicks on "MAKE SERVER CALL" button
        loginPage.makeServerCall.click();

        // Then "null" text should be shown in "server calls logs" text block
        loginPage.profileData.waitText(NULL_TEST_CAPTION);

        //End test
        endTest();
    }

    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.001-7 - Make server call for logged in user")
    public void makeServerCallForLoggedInUser() {
        // Given user is logged in
        String user = JDITestUtils.generateNewUserEmail();
        String password = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        SandboxAppSite.clickLogin();

        auth0Page.clickSignUpNewUser();
        auth0Page.inputApplicationUserName(user);
        auth0Page.inputApplicationUserPassword(password);
        auth0Page.clickApplicationSubmit();
        logger.info("Current URL: {}", getDriver().getCurrentUrl());
        DatabaseUtility.verifyUserExists();

        // And user navigates to "/login" page
        openPage(loginPage);

        // When user clicks on "MAKE SERVER CALL" button
        loginPage.makeServerCall.click();

        // Then JSON with user profile data should be shown in "server calls logs" text block
        loginPage.profileData.waitText(user);

        //End test
        endTest();
    }

    @Override
    public void endTest() {
        SandboxAppSite.endTest();
    }
}
