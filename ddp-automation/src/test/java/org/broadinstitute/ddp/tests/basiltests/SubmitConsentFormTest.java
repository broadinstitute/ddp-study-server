package org.broadinstitute.ddp.tests.basiltests;

import static org.broadinstitute.ddp.BasilAppSite.auth0Page;
import static org.broadinstitute.ddp.BasilAppSite.consentPage;
import static org.broadinstitute.ddp.BasilAppSite.dashboardPage;
import static org.broadinstitute.ddp.BasilAppSite.declinedConsentPage;
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

public class SubmitConsentFormTest extends BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(SubmitConsentFormTest.class);

    //Prequalifier input
    private static final boolean LIVES_IN_USA = true;
    private static final String USER_COUNTRY = "Canada";
    private static final boolean IS_FEMALE = true;
    private static final boolean HAVE_BEEN_DIAGNOSED = true;
    //    (Not Implemented! [Picklist])
    //private static final String USER_AGE_RANGE = "35- to 44-years old";
    //private static final String HEARD_ABOUT_US_METHOD = "From my healthcare provider";
    private String testUsername;
    private String testPassword;

    //Consent input
    private static final boolean AT_LEAST_TWENTY_ONE = true;
    private static final String AT_LEAST_TWENTY_ONE_DESCRIPTION = "at least 21";
    private static final boolean AGREE_TO_PARTICIPATE_IN_BASIL_STUDY = true;
    private static final String AGREE_TO_PARTICIPATE_IN_STUDY_DESCRIPTION = "agree to participate in basil study";
    private static final boolean AGREE_TO_SHARE_MEDICAL_RECORDS = true;
    private static final String AGREE_TO_SHARE_MEDICAL_RECORDS_DESCRIPTION = "agree to share medical records";
    private static final boolean AGREE_TO_SHARE_DNA = true;
    private static final String AGREE_TO_SHARE_GENETIC_INFO_DESCRIPTION = "agree to share dna";
    private static final String SIGNATURE = "webdriver test user";
    private static final String DATE_OF_BIRTH = "03/14/1988";
    private static final String RADIO_BUTTON_ANSWERED_TRUE = "Yes";
    private static final String RADIO_BUTTON_ANSWERED_FALSE = "No";
    private static final String RADIO_BUTTON_UNANSWERED = "Blank";

    //Given user opens https://basil-dev.datadonationplatform.org/basil-app/ in browser
    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        BasilAppWebsite.setDomain();
        return BasilAppSite.class;
    }

    @Before
    @Override
    public void background() {
        //    Given a new user
        //    And user opens https://basil-dev.datadonationplatform.org/basil-app in browser
        //    And user can see the "Get Started" button
        //    And user clicks said button
        homePage.clickLogin();

        //    And user sees the DDP authentication page
        //    And user clicks the "Sign Up" tab
        auth0Page.clickSignUpNewUser();

        //    And user enters new username referred to as testUsername
        testUsername = JDITestUtils.generateNewUserEmail();
        auth0Page.inputApplicationUserName(testUsername);

        //    And user enters new password referred to as testPassword
        testPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        auth0Page.inputApplicationUserPassword(testPassword);

        //    And user clicks "Sign Up" button
        auth0Page.clickApplicationSubmit();
        DatabaseUtility.verifyUserExists();

        //    And user is redirected to "basil-app/prequalifier"
        prequalifierPage.verifyPageIsOpened();

        //    And user answers "Yes" to first three questions
        prequalifierPage.setLiveInUSA(LIVES_IN_USA);
        prequalifierPage.setGender(IS_FEMALE);
        prequalifierPage.setHaveBeenDiagnosed(HAVE_BEEN_DIAGNOSED);

        //    (Not Implemented!)
        //    And user selects the age range "21- to 34-years old" for the next question

        //    (Not Implemented!)
        //    And user selects the option "From my healthcare provider" for the last question

        //    And user clicks the "Submit" button
        prequalifierPage.clickSubmit();

        //    And user is redirected to "basil-app/consent"
        //    And user sees a consent form
        consentPage.verifyPageIsOpened();
    }


    @Test
    @Category({FeatureTests.class})
    @DisplayName("010.01 - Completing entire form saves answers to server and updates consent status")
    public void testCompleteConsentAndUpdateUserStatus() {
        //    Given user answers "Yes" to "at least 21 years" question
        consentPage.setAtLeastTwentyone(AT_LEAST_TWENTY_ONE);

        //    And user answers "Yes" to "agree to participant" question
        consentPage.setAgreeToParticipateInBasilStudy(AGREE_TO_PARTICIPATE_IN_BASIL_STUDY);

        //    And user answers "Yes" to "Share Medical" question
        consentPage.setAgreeToShareMedicalRecords(AGREE_TO_SHARE_MEDICAL_RECORDS);

        //    And user answers "Yes" to "Share Genetic" question
        consentPage.setAgreeToShareGeneticInformation(AGREE_TO_SHARE_DNA);

        //    And user types in their name for "Signature" question
        consentPage.setSignature(SIGNATURE);

        //    And user types in 3 for month
        //    And user types in 14 for day
        //    And user types in 1988 for year
        consentPage.setBirthdayInformation(DATE_OF_BIRTH);

        //    And user clicks "Submit" button
        consentPage.clickSubmit();

        //    Then user is greeted with the Dashboard
        dashboardPage.verifyPageIsOpened();

        //    And user's answers should be saved
        //    And user's consent status should be true
        //    And user's election status for "Share Medical" should be true
        //    And user's election status for "Share Genetic" should be true

        //End test
        endTest();
    }


    @Test
    @Category({FeatureTests.class})
    @DisplayName("010.02 - After completing [and agreeing to] consent and logging back in, "
            + "user is not taken through consent flow again")
    public void testUserIsNotTakenThroughConsentAfterCompletionAndAgreeingToConsent() {
        //    Given user answers "Yes" to "at least 21 years" question
        consentPage.setAtLeastTwentyone(AT_LEAST_TWENTY_ONE);

        //    And user answers "Yes" to "agree to participant" question
        consentPage.setAgreeToParticipateInBasilStudy(AGREE_TO_PARTICIPATE_IN_BASIL_STUDY);

        //    And user types in their name for "Signature" question
        consentPage.setSignature(SIGNATURE);

        //    And user types in 03/14/1988 for the date
        consentPage.setBirthdayInformation(DATE_OF_BIRTH);

        //    And user clicks "Submit" button
        consentPage.clickSubmit();

        //    And user is presented with the Dashboard
        dashboardPage.verifyPageIsOpened();

        //    And user clicks the "Log out" button
        dashboardPage.clickBasilHeaderLogout();

        //    When user navigates to https://basil-dev.datadonationplatform.org/basil-app
        homePage.open();

        //    And the "Log in" button is present
        //    And user clicks said button
        homePage.clickLogin();

        //    And user sees the DDP authentication page
        auth0Page.verifyPageIsOpened();

        //    And user enters testUsername
        //    And user enters testPassword
        //    And user clicks "Log in" button
        auth0Page.clickHistoricalLogin();

        //    Then user should be greeted with the Dashoboard again
        dashboardPage.verifyPageIsOpened();

        //    And user's answers should be saved
        //    And user's consent status should be "Yes"
        //    And user's election status for "Share Medical" should be false
        //    And user's election status for "Share Genetic" should be false

        //End test
        endTest();
    }

    @Test
    @Category({FeatureTests.class})
    @DisplayName("010.02 - After completing [and declining to] consent and logging back in,"
            + " user is not taken through consent flow again")
    public void testUserIsNotTakenThroughConsentAfterCompletionAndRefusingToConsent() {
        //    Given user answers "Yes" to "at least 21 years" question
        consentPage.setAtLeastTwentyone(AT_LEAST_TWENTY_ONE);

        //    And user answers "No" to "agree to participant" question
        consentPage.setAgreeToParticipateInBasilStudy(!AGREE_TO_PARTICIPATE_IN_BASIL_STUDY);

        //    And user types in their name for "Signature" question
        consentPage.setSignature(SIGNATURE);

        //    And user types in 03/14/1988 for the date
        consentPage.setBirthdayInformation(DATE_OF_BIRTH);

        //    And user clicks "Submit" button
        consentPage.clickSubmit();

        //    And user is presented with the "declined to consent" page
        declinedConsentPage.verifyPageIsOpened();

        //    And user clicks the "Log out" button
        declinedConsentPage.clickBasilHeaderLogout();

        //    When user navigates to https://basil-dev.datadonationplatform.org/basil-app
        homePage.open();

        //    And the "Log in" button is present
        //    And user clicks said button
        homePage.clickBasilHeaderLogin();

        //    And user sees the DDP authentication page
        auth0Page.verifyPageIsOpened();

        //    And user enters testUsername
        //    And user enters testPassword
        //    And user clicks "Log in" button
        auth0Page.clickHistoricalLogin();

        //    Then user should be greeted with the "declined to consent" page again
        declinedConsentPage.verifyPageIsOpened();

        //    And user's answers should be saved
        //    And user's consent status should be "No"
        //    And user's election status for "Share Medical" should be false
        //    And user's election status for "Share Genetic" should be false

        //End test
        endTest();
    }


    @Test
    @Category({FeatureTests.class})
    @DisplayName("010.03 - Returning users are presented with where they left off with the consent flow")
    public void testAutomaticSaveOfConsentData() {
        //    Given user answers "Yes" to only the "at least 21 years" question
        consentPage.setAtLeastTwentyone(AT_LEAST_TWENTY_ONE);

        //    And none of the other questions are answered
        //    And user clicks the "Log out" button
        consentPage.clickBasilHeaderLogout();

        //    When user navigates to https://basil-dev.datadonationplatform.org/basil-app
        homePage.open();

        //    And the "Log in" button is present
        //    And user clicks said button
        homePage.clickLogin();

        //    And user sees the DDP authentication page
        auth0Page.verifyPageIsOpened();

        //    And user enters testUsername
        //    And user enters testPassword
        //    And user clicks "Log in" button
        auth0Page.clickHistoricalLogin();

        //    Then user should be presented with the consent form again
        consentPage.verifyPageIsOpened();
        consentPage.verifyConsentFormDisplayed();

        //    And only the "at least 21 years" question is answered with "Yes"
        consentPage.verifyBooleanQuestionStatus(AT_LEAST_TWENTY_ONE_DESCRIPTION, RADIO_BUTTON_ANSWERED_TRUE);

        //    And none of the other questions are answered
        consentPage.verifyBooleanQuestionStatus(AGREE_TO_PARTICIPATE_IN_STUDY_DESCRIPTION, RADIO_BUTTON_UNANSWERED);
        consentPage.verifyBooleanQuestionStatus(AGREE_TO_SHARE_MEDICAL_RECORDS_DESCRIPTION, RADIO_BUTTON_UNANSWERED);
        consentPage.verifyBooleanQuestionStatus(AGREE_TO_SHARE_GENETIC_INFO_DESCRIPTION, RADIO_BUTTON_UNANSWERED);
        consentPage.verifySignatureIsEmpty();
        consentPage.verifyDateOfBirthIsEmpty();

        //End test
        endTest();
    }

    @Test
    @Category({FeatureTests.class})
    @DisplayName("010.04 - User cannot submit consent form unless required questions are answered")
    public void testUserCanOnlySubmitConsentAfterCompletionOfRequiredQuestions() {
        //    Given user has not answered all required questions
        consentPage.setAtLeastTwentyone(AT_LEAST_TWENTY_ONE);

        //    And user clicks the "Submit" button
        consentPage.clickSubmit();

        //    Then error messages should be displayed
        consentPage.verifyValidationErrorsAreDisplayed();

        //    And user should not be navigated away from consent form
        consentPage.verifyPageIsOpened();

        //End test
        endTest();
    }

    @Test
    @Category({FeatureTests.class})
    @DisplayName("010.05 - User consents to study but does not agree to elections")
    public void testUserCanConsentToStudyWhileAlsoNotConsentingToElections() {
        //    Given user answers "Yes" to "at least 21 years" question
        consentPage.setAtLeastTwentyone(AT_LEAST_TWENTY_ONE);

        //    And user answers "Yes" to "agree to participant" question
        consentPage.setAgreeToParticipateInBasilStudy(AGREE_TO_PARTICIPATE_IN_BASIL_STUDY);

        //    And user answers "No" to "Share Medical" question
        consentPage.setAgreeToShareMedicalRecords(!AGREE_TO_SHARE_MEDICAL_RECORDS);

        //    And user answers "No" to "Share Genetic" question
        consentPage.setAgreeToShareGeneticInformation(!AGREE_TO_SHARE_DNA);

        //    And user types in their name for "Signature" question
        consentPage.setSignature(SIGNATURE);

        //    And user types in 03/14/1988 for the date
        consentPage.setBirthdayInformation(DATE_OF_BIRTH);

        //    And user clicks "Submit" button
        consentPage.clickSubmit();

        //    Then user is greeted with the Dashboard
        dashboardPage.verifyPageIsOpened();

        //    And user's answers should be saved
        //    And user's consent status should be true
        //    And user's election status for "Share Medical" should be false
        //    And user's election status for "Share Genetic" should be false

        //End test
        endTest();
    }

    @Override
    public void endTest() {
        consentPage.clickBasilHeaderLogout();
    }

}
