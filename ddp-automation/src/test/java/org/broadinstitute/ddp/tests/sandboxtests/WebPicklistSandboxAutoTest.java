package org.broadinstitute.ddp.tests.sandboxtests;

import static org.broadinstitute.ddp.DDPWebSite.CONFIG;
import static org.broadinstitute.ddp.SandboxAppSite.activityInstancePage;
import static org.broadinstitute.ddp.SandboxAppSite.activityInstancePrequalifier;
import static org.broadinstitute.ddp.SandboxAppSite.auth0Page;

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


public class WebPicklistSandboxAutoTest extends BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(WebPicklistSandboxAutoTest.class);

    private static final String AGE_RANGE_TWENTY_ONE_TO_THIRTY_FOUR = "21- to 34-years old";
    private static final String AGE_RANGE_FORTY_FIVE_TO_FIFTY_NINE = "45- to 59-years old";
    private static final String FROM_AD = "From an ad";
    private static final String FROM_HEALTHCARE_PROVIDER = "From my healthcare provider";

    private String testUser;
    private String testPassword;

    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        SandboxAppWebsite.setDomain();
        return SandboxAppSite.class;
    }

    @Before
    public void background() {
        //    Given a user has navigated to to https://basil-dev.datadonationplatform.org/sandbox-app/
        //    And user is logged in
        SandboxAppSite.clickLogin();

        auth0Page.clickSignUpNewUser();
        testUser = JDITestUtils.generateNewUserEmail();
        testPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);

        auth0Page.inputApplicationUserName(testUser);
        auth0Page.inputApplicationUserPassword(testPassword);
        auth0Page.clickApplicationSubmit();
        DatabaseUtility.verifyUserExists();

        //    Open https://basil-dev.datadonationplatform.org/sandbox-app/activity
        openPage(activityInstancePage);

        //    And user is redirected to https://basil-dev.datadonationplatform.org/sandbox-app/activity
        activityInstancePage.verifyPageIsOpened();

        //    And user can see the activity loaded
        //    And user can answer boolean questions of the activity
        //    And user can answer text questions of the activity
        //    And user can see the picklist questions of the activity
        activityInstancePage.waitUntilActivityDisplayed();
    }

    @Ignore("Until QA picklist logic is fixed")
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.003-01 Select one option for a question that only allows one option and "
            + "verify the selected option was saved")
    public void testSingleChoicePicklist() {
        //    Given a user sees the question "Which group represents your age?"
        //    And the question only allows one answer
        //    And the question has many possible answers such as "21- to 34-years old, 45- to 59-years old, etc."
        activityInstancePrequalifier.waitUntilPrequalifierQuestionsDisplayed();

        //    When the user selects "21- to 34-years old"
        activityInstancePrequalifier.selectUserAgeRange(AGE_RANGE_TWENTY_ONE_TO_THIRTY_FOUR);

        //    Then the selected option should be saved as the user's answer
        activityInstancePrequalifier.clickSubmit();

        //End test
        endTest();
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.003-02 See an error message when user has failed to pick an option for a required question")
    public void testErrorForUnansweredRequiredQuestion() {
        //    Given a user does not answer the required question "Which group represents your age?"
        //    When the user clicks "submit"
        activityInstancePrequalifier.clickSubmit();

        //    Then the user should see "<an error message>" near the required question
        activityInstancePrequalifier.verifyAgeRangeValidationErrorDisplayed();

        //End test
        endTest();
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.003-03 See no error when nothing is selected for a picklist whose answer is not required")
    public void testNoErrorForUnAnsweredOptionalQuestion() {
        //    Given a user sees the non-required question "How did you hear about this study?"
        //    And the question allows more than one possible answer such as "From an ad, By searching online, etc."
        //    But user does not select an option
        activityInstancePrequalifier.waitUntilPrequalifierQuestionsDisplayed();

        //*Filling out required question
        activityInstancePrequalifier.selectUserAgeRange(AGE_RANGE_TWENTY_ONE_TO_THIRTY_FOUR);

        //    When the user clicks "submit"
        activityInstancePrequalifier.clickSubmit();

        //    Then no error message should be shown
        activityInstancePrequalifier.verifyNoValidationMessagesDisplayed();

        //End test
        endTest();
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.003-04 Automatic saving of selected answers without clicking submit button")
    public void testAutomaticSavingOfAnswers() {
        //    Given a user sees a question
        //    And the question allows more than one possible answer
        activityInstancePrequalifier.waitUntilPrequalifierQuestionsDisplayed();

        //    And user selects an option
        activityInstancePrequalifier.selectHowHearAboutUs(FROM_AD);

        //    But user logs out
        SandboxAppSite.clickLogout();

        //    And user later logs in
        SandboxAppSite.clickLogin();
        auth0Page.clickHistoricalLogin();
        DatabaseUtility.verifyUserExists();

        //    And user navigates back to the question
        openPage(activityInstancePage);
        activityInstancePage.verifyPageIsOpened();
        activityInstancePage.waitUntilActivityDisplayed();

        activityInstancePrequalifier.waitUntilPrequalifierQuestionsDisplayed();

        //    When user sees the question
        //    Then it should still have user's previous answer selected
        activityInstancePrequalifier.verifyHowHeardAboutStudySelectedAnswers(FROM_AD);

        //End test
        endTest();
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.003-05 Ability to un-answer a question by deselecting previously selection option(s)")
    public void testAbilityToUnanswerQuestionUsingDeselection() {
        //    Given  a user sees the question "How did you hear about this study?
        //    And the question allows more than one possible answer such as "From an ad, By searching online,
        //    etc."
        activityInstancePrequalifier.waitUntilPrequalifierQuestionsDisplayed();

        //    And user has previously selected "From an ad, From my healthcare provider"
        activityInstancePrequalifier.selectHowHearAboutUs(FROM_AD);
        activityInstancePrequalifier.selectHowHearAboutUs(FROM_HEALTHCARE_PROVIDER);

        //    When the user selects the previously selected option "From an ad" again
        activityInstancePrequalifier.selectHowHearAboutUs(FROM_AD);

        //    Then the option should be de-selected
        activityInstancePrequalifier.verifyHowHeardAboutStudyOptionUnselected(FROM_AD);

        //    And the user's final answer(s) should be "From my healthcare provider"
        activityInstancePrequalifier.verifyHowHeardAboutStudySelectedAnswers(FROM_HEALTHCARE_PROVIDER);

        //End test
        endTest();
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.003-06 Select multiple options for a question that allows more than one item to be selected")
    public void testMultipleChoiceAbility() {
        //    Given  a user sees the question "How did you hear about this study?"
        //    And the question allows for more than one possible answer such as "From an ad, By searching online, etc."
        activityInstancePrequalifier.waitUntilPrequalifierQuestionsDisplayed();

        //    When the user selects "From an ad, From my healthcare provider"
        activityInstancePrequalifier.selectHowHearAboutUs(FROM_AD);
        activityInstancePrequalifier.selectHowHearAboutUs(FROM_HEALTHCARE_PROVIDER);

        //    Then the selected options should be saved as the user's answers
        activityInstancePrequalifier.verifyHowHeardAboutStudySelectedAnswers(FROM_AD,
                FROM_HEALTHCARE_PROVIDER);

        //End test
        endTest();
    }

    @Ignore
    @Test
    @Category({SandboxTests.class})
    @DisplayName("S.003-07 Cannot select multiple options for question that only supports single selection")
    public void testSingleChoiceQuestionAcceptsOnlyOneAnswer() {
        //    Given a user sees the question "Which group represents your age?"
        //    And the question only allows one answer
        //    And the question has many possible answers such as "21- to 34-years old, 45- to 59-years old, etc."
        activityInstancePrequalifier.waitUntilPrequalifierQuestionsDisplayed();

        //    And user has selected "21- to 34-years old"
        activityInstancePrequalifier.selectUserAgeRange(AGE_RANGE_TWENTY_ONE_TO_THIRTY_FOUR);

        //    But user also wants to select "45- to 59-years old"
        //    When user selects "45- to 59-years old"
        activityInstancePrequalifier.selectUserAgeRange(AGE_RANGE_FORTY_FIVE_TO_FIFTY_NINE);

        //    Then "21- to 34-years old" should be de-selected
        activityInstancePrequalifier.verifyAgeRangeUnselected(AGE_RANGE_TWENTY_ONE_TO_THIRTY_FOUR);

        //    And "45- to 59-years old" should be selected instead
        activityInstancePrequalifier.verifyAgeRangeSelected(AGE_RANGE_FORTY_FIVE_TO_FIFTY_NINE);

        //End test
        endTest();
    }

    @Override
    public void endTest() {
        SandboxAppSite.clickLogout();
    }
}
