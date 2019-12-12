package org.broadinstitute.ddp.tests.basiltests;

import static org.broadinstitute.ddp.BasilAppSite.auth0Page;
import static org.broadinstitute.ddp.BasilAppSite.consentPage;
import static org.broadinstitute.ddp.BasilAppSite.dashboardPage;
import static org.broadinstitute.ddp.BasilAppSite.homePage;
import static org.broadinstitute.ddp.BasilAppSite.prequalifierPage;
import static org.broadinstitute.ddp.DDPWebSite.CONFIG;

import org.broadinstitute.ddp.BasilAppSite;
import org.broadinstitute.ddp.BasilAppWebsite;
import org.broadinstitute.ddp.ConfigFile;
import org.broadinstitute.ddp.DDPWebSite;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
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


public class BasilAppAutoTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BasilAppAutoTest.class);

    //Prequalifier related variables
    private static final boolean LIVES_IN_USA = false;
    private static final String USER_COUNTRY = "Canada";
    private static final boolean IS_FEMALE = true;
    private static final boolean HAVE_BEEN_DIAGNOSED = false;
    private static final String USER_AGE_RANGE = "75-years old and up";
    private static final String HEARD_ABOUT_US_METHOD = "From an ad";

    //Consent related variables
    private static final boolean AT_LEAST_TWENTY_ONE = true;
    private static final boolean AGREE_TO_PARTICIPATE_IN_BASIL_STUDY = true;
    private static final boolean AGREE_TO_SHARE_MEDICAL_RECORDS = true;
    private static final boolean AGREE_TO_SHARE_DNA = true;
    private static final String SIGNATURE = "webdriver test user";
    private static final String DATE_OF_BIRTH = "03/14/1988";

    String testUser;
    String testPassword;

    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        BasilAppWebsite.setDomain();
        return BasilAppSite.class;
    }

    @Before
    @Override
    public void background() {
        //Login
        homePage.clickLogin();

        //Create a new user and password
        testUser = JDITestUtils.generateNewUserEmail();
        testPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);
    }

    @Test
    @Category(SmokeTests.class)
    @DisplayName("Basil-App: end-to-end test")
    public void test() {
        //Register using new credentials
        auth0Page.clickSignUpNewUser();
        auth0Page.inputApplicationUserName(testUser);
        auth0Page.inputApplicationUserPassword(testPassword);
        auth0Page.clickApplicationSubmit();
        DatabaseUtility.verifyUserExists();

        //Complete prequalifier
        prequalifierPage.setLiveInUSA(LIVES_IN_USA);
        prequalifierPage.setCountryOfResidence(USER_COUNTRY);
        prequalifierPage.setGender(IS_FEMALE);
        prequalifierPage.setHaveBeenDiagnosed(HAVE_BEEN_DIAGNOSED);

        //Submit prequalifier
        prequalifierPage.clickSubmit();

        //Complete consent
        consentPage.setAtLeastTwentyone(AT_LEAST_TWENTY_ONE);
        consentPage.setAgreeToParticipateInBasilStudy(AGREE_TO_PARTICIPATE_IN_BASIL_STUDY);
        consentPage.setAgreeToShareMedicalRecords(AGREE_TO_SHARE_MEDICAL_RECORDS);
        consentPage.setAgreeToShareGeneticInformation(AGREE_TO_SHARE_DNA);
        consentPage.setSignature(SIGNATURE);
        consentPage.setBirthdayInformation(DATE_OF_BIRTH);

        //Submit consent
        consentPage.clickSubmit();

        //See dashboard
        dashboardPage.verifyPageIsOpened();
        JDIPageUtils.scrollToTopOfPage();

        //End test
        endTest();
    }


    @Override
    public void endTest() {
        dashboardPage.clickBasilHeaderLogout();
    }
}
