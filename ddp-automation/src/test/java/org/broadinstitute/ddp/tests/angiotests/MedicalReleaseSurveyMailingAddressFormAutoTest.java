package org.broadinstitute.ddp.tests.angiotests;

import static org.broadinstitute.ddp.AngioAppSite.aboutYouPage;
import static org.broadinstitute.ddp.AngioAppSite.auth0Page;
import static org.broadinstitute.ddp.AngioAppSite.consentPage;
import static org.broadinstitute.ddp.AngioAppSite.countMeInPage;
import static org.broadinstitute.ddp.AngioAppSite.gatekeeperPage;
import static org.broadinstitute.ddp.AngioAppSite.homePage;
import static org.broadinstitute.ddp.AngioAppSite.medicalReleaseFormPage;
import static org.broadinstitute.ddp.DDPWebSite.CONFIG;

import org.broadinstitute.ddp.AngioAppSite;
import org.broadinstitute.ddp.AngioWebsite;
import org.broadinstitute.ddp.ConfigFile;
import org.broadinstitute.ddp.DDPWebSite;
import org.broadinstitute.ddp.tests.BaseTest;
import org.broadinstitute.ddp.tests.DatabaseUtility;
import org.broadinstitute.ddp.tests.SmokeTests;
import org.broadinstitute.ddp.util.JDITestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created to test the mailing address form feature in medical release survey on pepper-angio-test
 * due to needing to check for cosmetic consistency between browsers
 * (251 countries to check == 753 entries between 3 browsers [Chrome, Safari, Firefox])
 */
public class MedicalReleaseSurveyMailingAddressFormAutoTest extends BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(MedicalReleaseSurveyMailingAddressFormAutoTest.class);

    //Diagnosed status [/count-me-in]
    private static final String DIAGNOSED_WITH_ANGIOSARCOMA = "diagnosed with angiosarcoma";

    //Test user's birth information
    private static final String BIRTH_INFORMATION = "11/25/1990";
    private static final String UNITED_STATES_ZIPCODE = "02142";

    //Generic response choices for radio buttons
    private static final String RESPONSE_YES = "Yes";
    private static final String RESPONSE_NO = "No";
    private static final String RESPONSE_I_DONT_KNOW = "I don\'t know";

    //For new user creation
    String testUserFirstName;
    String testUserLastName;
    String testUserFullName;

    //New user's email registration
    String testUserEmail;
    String testUserPassword;

    //Mailing address variables to test for

    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        AngioWebsite.setDomain();
        return AngioAppSite.class;
    }

    @Override
    @Before
    public void background() {
        //Create user
        testUserFirstName = JDITestUtils.generateNewUserName(FIRST_NAME_DESCRIPTION);
        testUserLastName = JDITestUtils.generateNewUserName(LAST_NAME_DESCRIPTION);
        testUserFullName = JDITestUtils.getFullName(testUserFirstName, testUserLastName);

        testUserEmail = JDITestUtils.generateNewUserEmail();
        testUserPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
        logger.info("Test Username: {}", testUserFullName);

        //Get past the gatekeeper
        gatekeeperPage.verifyPageIsOpened();
        DatabaseUtility.unlockGatekeeperPage(gatekeeperPage);
        gatekeeperPage.clickSubmit();

        //Navigate to /count-me-in, fill out form, and select "I have been diagnosed..."
        homePage.verifyPageIsOpened();
        homePage.clickCountMeIn();

        countMeInPage.verifyPageIsOpened();
        countMeInPage.inputFirstName(testUserFirstName);
        countMeInPage.inputLastName(testUserLastName);
        countMeInPage.selectAngiosarcomaStatus(DIAGNOSED_WITH_ANGIOSARCOMA);
        countMeInPage.clickSubmit();

        //Register
        auth0Page.verifyPageIsOpened();
        auth0Page.clickSignUpNewUser();
        auth0Page.inputApplicationUserName(testUserEmail);
        auth0Page.inputApplicationUserPassword(testUserPassword);
        auth0Page.clickApplicationSubmit();

        //Submit /about-you
        aboutYouPage.verifyPageIsOpened();
        aboutYouPage.clickSubmit();

        //Complete and submit /consent
        //1. Key Points
        consentPage.verifyPageIsOpened();
        consentPage.verifyGeneralConsentContentDisplayed();
        consentPage.clickNext();

        //2. Full Form
        consentPage.verifyGeneralConsentContentDisplayed();
        consentPage.clickNext();

        //3. Sign Consent
        consentPage.setAgreeToSampleOfBloodBeingDrawn(RESPONSE_YES);
        consentPage.setAgreeToStoredTissueSample(RESPONSE_YES);
        consentPage.inputSignature(testUserFullName);
        consentPage.setDateOfBirth(BIRTH_INFORMATION);

        consentPage.clickSubmit();
    }

    @Test
    @Category(SmokeTests.class)
    @DisplayName("Mailing Address: Test countries other than USA/CANADA have a state/province field as text input")
    public void testMailingAddressFormHasCorrectFormFieldsForAllCountries() {
        medicalReleaseFormPage.verifyPageIsOpened();
        //todo finish creating test
    }

    @Override
    public void endTest() {

    }
}
