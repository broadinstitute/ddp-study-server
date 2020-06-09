package org.broadinstitute.ddp.tests.basiltests;

import static org.broadinstitute.ddp.DDPWebSite.CONFIG;
import static org.broadinstitute.ddp.earlypepper.BasilAppSite.auth0Page;
import static org.broadinstitute.ddp.earlypepper.BasilAppSite.dashboardPage;
import static org.broadinstitute.ddp.earlypepper.BasilAppSite.homePage;
import static org.broadinstitute.ddp.earlypepper.BasilAppSite.notQualifiedPage;
import static org.broadinstitute.ddp.earlypepper.BasilAppSite.prequalifierActivityPage;
import static org.broadinstitute.ddp.earlypepper.BasilAppSite.prequalifierPage;

import org.broadinstitute.ddp.ConfigFile;
import org.broadinstitute.ddp.DDPWebSite;
import org.broadinstitute.ddp.earlypepper.BasilAppSite;
import org.broadinstitute.ddp.earlypepper.BasilAppWebsite;
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


public class SubmitPrequalifierTest extends BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(SubmitPrequalifierTest.class);

    private static final boolean LIVES_IN_USA = false;
    private static final String FOREIGN_COUNTRY_OF_RESIDENCE = "Canada";
    private static final boolean IS_FEMALE = true;
    private static final boolean HAVE_BEEN_DIAGNOSED = false;
    private static final String USER_AGE_RANGE = "75-years old and up";
    private static final String HEARD_ABOUT_US_METHOD = "From an ad";
    private String testUser;
    private String testUserPassword;

    //Given user opens https://basil-dev.datadonationplatform.org/basil-app/ in browser
    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        BasilAppWebsite.setDomain();
        return BasilAppSite.class;
    }

    @Before
    @Override
    public void background() {
        //And user can see "login" button
        //And user clicks "login" button
        homePage.clickLogin();

        //And user clicks "sign up" tab
        auth0Page.clickSignUpNewUser();

        //And user enters new username "test@test.org" **example, not actual input
        testUser = JDITestUtils.generateNewUserEmail();
        auth0Page.inputApplicationUserName(testUser);

        //And user enters new password "123456" **example, not actual input
        testUserPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        auth0Page.inputApplicationUserPassword(testUserPassword);

        //And user clicks "sign up" button
        auth0Page.clickApplicationSubmit();
        DatabaseUtility.verifyUserExists();

        //And user is redirected to https://basil-dev.datadonationplatform.org/basil-app/prequalifier
        prequalifierPage.verifyOpened();

        //And user can see prequalifier text content
        //And user can see prequalifier questions
        //And user can see "submit" button
        prequalifierPage.assertPrequalifierElementsAreDisplayed();
    }

    @Test
    @Category({FeatureTests.class})
    @DisplayName("004.01 - Full save of completed prequalifier data")
    public void testSubmitCompletePrequalifier() {
        //Given a user clicks on all answer responses
        prequalifierPage.setLiveInUSA(LIVES_IN_USA);

        //Uncomment below to throw exception if user lives in USA
        prequalifierPage.setCountryOfResidence(FOREIGN_COUNTRY_OF_RESIDENCE);
        prequalifierPage.setGender(IS_FEMALE);
        prequalifierPage.setHaveBeenDiagnosed(HAVE_BEEN_DIAGNOSED);

        //When user clicks "submit" button
        prequalifierPage.clickSubmit();

        //End test
        endTest();
    }

    @Test
    @Category({FeatureTests.class})
    @DisplayName("004.02 - User attempts to submit prequalifier form without answering all responses")
    public void testSubmitIncompletePrequalifier() {
        //Given a user answers a question
        prequalifierPage.setLiveInUSA(LIVES_IN_USA);

        //When user clicks "submit" button
        prequalifierPage.clickSubmit();

        //But user has not answered all required questions
        //Then system displays message "An answer is required for this question"
        //And system displays above message for each unanswered question that requires a response
        //And user is not redirected from the prequalifier page
        prequalifierPage.verifyOpened();

        //End test
        endTest();
    }

    @Test
    @Category({FeatureTests.class})
    @DisplayName("004.03 - Automatic 'in-flight' save of prequalifier data without user clicking the [Submit] button")
    public void testAutomaticSaveOfPrequalifierData() {
        //Given a user answers a question
        prequalifierPage.setLiveInUSA(LIVES_IN_USA);

        //When user leaves prequalifier form
        homePage.open();

        //But user has not clicked "submit" button
        //And user logs out
        homePage.clickBasilHeaderLogout();

        //And user later logs in
        homePage.clickLogin();
        auth0Page.clickHistoricalLogin();

        //    And user is redirected to https://basil-dev.datadonationplatform.org/basil-app/not-qualified
        //    And user sees "Sorry, You are not qualified..." content
        notQualifiedPage.verifyPageIsOpened();

        //    And user navigates to https://basil-dev.datadonationplatform.org/basil-app/dashboard
        openPage(dashboardPage);

        dashboardPage.verifyPageIsOpened();

        //    And user clicks the prequalifier "Join the Basil Research Study!"
        dashboardPage.clickBasilStudy();

        //    And user is redirected to https://basil-dev.datadonationplatform.org/basil-app/activity
        prequalifierActivityPage.verifyPageIsOpened();

        //    Then user's previously completed responses on the form should still be answered
        prequalifierActivityPage.verifyLiveInUSAAnswerSelected(LIVES_IN_USA);

        //    And user should be able to answer the rest of the questions
        prequalifierActivityPage.setCountryOfResidence(FOREIGN_COUNTRY_OF_RESIDENCE);
        prequalifierActivityPage.setGender(IS_FEMALE);
        prequalifierActivityPage.setHaveBeenDiagnosed(HAVE_BEEN_DIAGNOSED);

        //    And user should be able to click "submit" button
        prequalifierActivityPage.clickSubmit();

        //    And user's data should be saved

        //End test
        endTest();
    }

    @Override
    public void endTest() {
        prequalifierPage.clickBasilHeaderLogout();
    }
}
