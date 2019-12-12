package org.broadinstitute.ddp.tests.sandboxtests;

import static org.broadinstitute.ddp.DDPWebSite.CONFIG;
import static org.broadinstitute.ddp.SandboxAppSite.auth0Page;
import static org.broadinstitute.ddp.SandboxAppSite.userProfilePage;

import org.broadinstitute.ddp.ConfigFile;
import org.broadinstitute.ddp.DDPWebSite;
import org.broadinstitute.ddp.SandboxAppSite;
import org.broadinstitute.ddp.SandboxAppWebsite;
import org.broadinstitute.ddp.tests.BaseTest;
import org.broadinstitute.ddp.tests.DatabaseUtility;
import org.broadinstitute.ddp.tests.SandboxTests;
import org.broadinstitute.ddp.util.JDITestUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserProfileSandboxAutoTest extends BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(UserProfileSandboxAutoTest.class);
    //Use MM/DD/YYYY format
    private static final String BIRTH_INFO = "01/01/1970";
    private static final String NEW_BIRTH_INFO = "02/29/2004";
    private static final String INVALID_BIRTH_INFO = "02/30/2005";

    //Value of SEX in app is in all caps with underscores for spaces
    private static final String SEX_PREFER_NOT_TO_ANSWER = "PREFER_NOT_TO_ANSWER";
    private static final String SEX_FEMALE = "FEMALE";

    //Value of PREFERRED_LANGUAGE in app is language codes e.g. en (English), ru (Russian), fr (French)
    private static final String PREFERRED_LANGUAGE_FRENCH = "fr";
    private static final String PREFERRED_LANGUAGE_ENGLISH = "en";

    private String testUser;
    private String testPassword;

    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        SandboxAppWebsite.setDomain();
        return SandboxAppSite.class;
    }

    @Before
    @Override
    public void background() {
        //    Given user is logged in
        testUser = JDITestUtils.generateNewUserEmail();
        testPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        SandboxAppSite.clickLogin();

        auth0Page.clickSignUpNewUser();
        auth0Page.inputApplicationUserName(testUser);
        auth0Page.inputApplicationUserPassword(testPassword);
        auth0Page.clickApplicationSubmit();
        DatabaseUtility.verifyUserExists();

        //    When user is navigated to "/userprofile" page
        openPage(userProfilePage);
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.002-01 - Default initial state for user profile")
    public void testDefaultState() {
        //    Then "Profile" button should be visible
        userProfilePage.verifyUserProfileButtonDsplayed();

        //    And "Profile" button should have testUser caption
        userProfilePage.verifyUserProfileButtonCaption(testUser);

        //End test
        endTest();
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.002-02 - Open user preferences")
    public void testOpenUserPreferences() {
        //    And user clicks on "Profile" button
        userProfilePage.clickUserProfile();

        //    Then the following controls should be visible:
        //    | control     |
        //    | birth month |
        //    | birth date  |
        //    | birth year  |
        //    | gender      |
        //    | locale      |
        userProfilePage.verifyUserProfilePopupDisplayed();

        //End test
        endTest();
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.002-03 - Fill out birthdate in user profile")
    public void testAbilityToFillOutBirthdate() {
        //    Given a user wants to fill in their birthdate information
        userProfilePage.clickUserProfile();

        //    When user selects the "birth month" drop down menu
        //    And user sees "Month" as the caption of the drop down menu
        //    And user selects "1"
        //    And user selects the "birth date" drop down menu
        //    And user sees "Date" as the caption of the drop down menu
        //    And user selects "1"
        //    And user selects "Year of birth" drop down menu
        //    And user sees "Year" as the caption of the drop down menu
        //    And user selects "1970"
        userProfilePage.setBirthInformation(BIRTH_INFO);

        //    And user clicks the "save" button
        userProfilePage.clickSave();

        //End test
        endTest();

        //    Then user data should be saved
        //    Examples:
        //      | month | day | year |
        //      |   6   | 31  | 1970 |
        //      |  10   | 29  | 1992 |
        //      |   2   | 30  | 2005 |
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.002-04 - Fill out sex in user profile")
    public void testAbilityToFillOutSex() {
        //    Given a user wants to fill in their sex information
        userProfilePage.clickUserProfile();

        //    When user selects the "sex" drop down menu
        //    And user sees "Sex" as the caption of the drop down menu
        //    And user selects "Female"
        userProfilePage.setSex(SEX_FEMALE);

        //    And user user clicks the "save" button
        userProfilePage.clickSave();

        //End test
        endTest();

        //    Then user data should be saved
        //    Examples:
        //      |           sex        |
        //      |          Male        |
        //      |         Female       |
        //      |       Intersex       |
        //      | Prefer not to answer |
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.002-05 - Fill out preferred language in user profile")
    public void testAbilityToFillOutPreferredLanguage() {
        //    Given a user wants to fill in their preferred language information
        userProfilePage.clickUserProfile();

        //    When user selects the "preferred language" drop down menu
        //    And user selects "English"
        userProfilePage.setPreferredLanguage(PREFERRED_LANGUAGE_ENGLISH);

        //    And user clicks the "save" button
        userProfilePage.clickSave();

        //End test
        endTest();

        //    Then user data should be saved
        //    Examples:
        //      | language |
        //      | English  |
        //      |  French  |
        //      | Russian  |
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.002-06 - Cancel unsaved changes in user profile")
    public void testAbilityToCancelUnsavedChanges() {
        //    Given a user wants to change profile information in:
        //          birthdate[ 1/1/1970 ]
        //          gender[ Female ]
        //          language[ English ]
        userProfilePage.clickUserProfile();
        userProfilePage.setBirthInformation(BIRTH_INFO);
        userProfilePage.setSex(SEX_FEMALE);
        userProfilePage.setPreferredLanguage(PREFERRED_LANGUAGE_ENGLISH);
        userProfilePage.clickSave();

        //    And user wants to enter this new data instead:
        //          birthdate[ 2/29/2004 ]
        //          gender[ Prefer not to answer ]
        //          language[ French ]
        //    When user selects the relevant drop down menus
        //    And user inputs the new data
        userProfilePage.clickUserProfile();
        userProfilePage.setBirthInformation(NEW_BIRTH_INFO);
        userProfilePage.setSex(SEX_PREFER_NOT_TO_ANSWER);
        userProfilePage.setPreferredLanguage(PREFERRED_LANGUAGE_FRENCH);

        //    But user changes their mind
        //    And user selects "cancel" button
        userProfilePage.clickCancel();

        //    Then newly entered profile data should not be saved
        userProfilePage.clickUserProfile();
        userProfilePage.verifyBirthInformationSetting(BIRTH_INFO);
        userProfilePage.verifySexSetting(SEX_FEMALE);
        userProfilePage.verifyPreferredLanguageSetting(PREFERRED_LANGUAGE_ENGLISH);

        //End test
        endTest();
        //    Examples:
        //      |       data       |    previous data |           new data          |
        //      |     birthdate    |      1/1/1970    |           2/8/2005          |
        //      |      gender      |      Female      |    Prefer not to answer     |
        //      |     language     |      English     |           French            |
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.002-07 - Can fill out user profile")
    public void testAbilityToCompleteUserProfile() {
        //    Given a user wants to fill out their profile
        userProfilePage.clickUserProfile();

        //    When user selects "month" drop down menu
        //    And user selects "1" as their birth month
        //    And user selects "date" drop down menu
        //    And user selects "1" as their birth date
        //    And user selects "year" drop down menu
        //    And user selects "1970" as their birth year
        userProfilePage.setBirthInformation(BIRTH_INFO);

        //    And user selects the "sex" drop down menu
        //    And user selects "Female" as their sex
        userProfilePage.setSex(SEX_FEMALE);

        //    And user selects the "preferred language" drop down menu
        //    And user selects "English" as their preferred language
        userProfilePage.setPreferredLanguage(PREFERRED_LANGUAGE_ENGLISH);

        //    And user selects "save" button
        userProfilePage.clickSave();

        //End test
        endTest();

        //    Then user data should be saved
        //    Examples:
        //      | month | day | year |         sex          |   language    |
        //      |  12   | 25  | 1998 |        Male          |   English     |
        //      |   2   | 29  | 2016 |       Female         |    French     |
        //      |   9   | 31  | 1970 | Prefer not to answer |   Russian     |
    }

    @Override
    public void endTest() {
        //Exit user profile
        userProfilePage.exitUserProfile();

        //Logout
        SandboxAppSite.clickLogout();
    }

}
