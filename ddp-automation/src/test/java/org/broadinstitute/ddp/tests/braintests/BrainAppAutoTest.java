package org.broadinstitute.ddp.tests.braintests;

import static org.broadinstitute.ddp.BrainAppSite.aboutYouPage;
import static org.broadinstitute.ddp.BrainAppSite.auth0Page;
import static org.broadinstitute.ddp.BrainAppSite.consentPage;
import static org.broadinstitute.ddp.BrainAppSite.countMeInPage;
import static org.broadinstitute.ddp.BrainAppSite.dashboardPage;
import static org.broadinstitute.ddp.BrainAppSite.gatekeeperPage;
import static org.broadinstitute.ddp.BrainAppSite.homePage;
import static org.broadinstitute.ddp.BrainAppSite.joinMailingList;
import static org.broadinstitute.ddp.BrainAppSite.medicalReleaseFormPage;
import static org.broadinstitute.ddp.BrainAppSite.postConsentSurveyPage;
import static org.broadinstitute.ddp.BrainAppSite.stayInformedPage;
import static org.broadinstitute.ddp.DDPWebSite.CONFIG;

import org.broadinstitute.ddp.BrainAppSite;
import org.broadinstitute.ddp.BrainWebsite;
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

public class BrainAppAutoTest extends BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(BrainAppAutoTest.class);

    //Different diagnosed statuses [/count-me-in]
    private static final String DIAGNOSED_WITH_BRAIN_CANCER = "diagnosed with brain cancer";
    private static final String NOT_DIAGNOSED_WITH_BRAIN_CANCER_BUT_WANT_TO_STAY_INFORMED = "not diagnosed but want to stay informed";

    //Variables for /about-you
    private static final String DIAGNOSIS_MONTH = "June";
    private static final String DIAGNOSIS_YEAR = "2000";
    private static final String DIAGNOSIS_YEAR_OF_OTHER_BRAIN_CANCER_TYPE = "2005";
    private static final String DIAGNOSIS_MONTH_OF_OTHER_BRAIN_CANCER_TYPE = "July";

    private static final String BRAIN_CANCER_INITIAL_DIAGNOSIS = "Oligodendroglioma";
    private static final String BRAIN_CANCER_SECONDARY_DIAGNOSIS = "Glioblastoma multiforme (GBM)";

    private static final String FIRST_GRADE = "I";
    private static final String SECOND_GRADE = "II";
    private static final String THIRD_GRADE = "III";
    private static final String FOURTH_GRADE = "IV";

    private static final String RESPONSE_YES = "Yes";
    private static final String RESPONSE_NO = "No";
    private static final String RESPONSE_UNSURE_HOW_TO_ANSWER = "Unsure how to answer";
    private static final String RESPONSE_I_DONT_KNOW = "I don\'t know";

    private static final String BIRTH_YEAR = "1995";
    private static final String BIRTH_INFORMATION = "11/11/1995";

    private static final String COUNTRY_OF_RESIDENCE = "United States";
    private static final String RESIDENTIAL_ZIPCODE = "02142";

    private static final String SEX_MALE = "Male";
    private static final String SEX_FEMALE = "Female";
    private static final String SEX_INTERSEX = "Intersex";

    private static final String GENDER_WOMAN = "Woman";
    private static final String GENDER_MAN = "Man";
    private static final String GENDER_NOT_LISTED = "Not listed";
    private static final String GENDER_I_PREFER_NOT_TO_ANSWER = "I prefer not to answer";

    private static final String ETHNICITY_AMERICAN_INDIAN_OR_NATIVE_AMERICAN = "American Indian or Native American";
    private static final String ETHNICITY_JAPANESE = "Japanese";
    private static final String ETHNICITY_CHINESE = "Chinese";
    private static final String ETHNICITY_OTHER_EAST_ASIAN = "Other East Asian";
    private static final String ETHNICITY_SOUTH_EAST_ASIAN_OR_INDIAN = "South East Asian or Indian";
    private static final String ETHNICITY_BLACK_OR_AFRICAN_AMERICAN = "Black or African American";
    private static final String ETHNICITY_NATIVE_HAWAIIAN_OR_OTHER_PACIFIC_ISLANDER = "Native Hawaiian or other Pacific Islander";
    private static final String ETHNICITY_WHITE = "White";
    private static final String ETHNICITY_I_PREFER_NOT_TO_ANSWER = "I prefer not to answer";
    private static final String ETHNICITY_OTHER = "Other";
    private static final String ETHNICITY_OTHER_DETAILS = "Gallifreyan";

    private static final String METHOD_OF_LEARNING_ABOUT_BRAIN_CANCER_PROJECT = "Ad on a bus.";

    //Variables for medical release
    private static final String COUNTRY_UNITED_STATES = "UNITED STATES";
    private static final String UNITED_STATES_STREET_ONE_ADDRESS = "105 Broadway";
    private static final String UNITED_STATES_STREET_TWO_ADDRESS = "ATTN: Broad Institute";
    private static final String UNITED_STATES_CITY = "Cambridge";
    private static final String UNITED_STATES_STATE = "MASSACHUSETTS";
    private static final String UNITED_STATES_ZIPCODE = "02142";
    private static final String PHONE_NUMBER = "123-456-7890";

    //Physician information
    private static final String PHYSICIAN_NAME = "Doctor Nicolas Riveria";
    private static final String PHYSICIAN_INSTITUTION = "OSHU Hospital";
    private static final String PHYSICIAN_CITY = "Portland";
    private static final String PHYSICIAN_STATE = "Oregon";

    //Initial biopsy hospital/institution information
    private static final String INITIAL_BIOPSY_INSTITUTION_NAME = "UCLA Medical Center";
    private static final String INITIAL_BIOPSY_INSTITUTION_CITY = "Los Angeles";
    private static final String INITIAL_BIOPSY_INSTITUTION_STATE = "California";

    //Additional biopsies/surgeries information
    private static final String ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_NAME = "UCSF Medical Center";
    private static final String ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_CITY = "San Francisco";
    private static final String ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_STATE = "California";

    //Brain cancer surgical procedures
    private static final String SURGICAL_PROCEDURE_BIOPSY = "Biopsy";

    private static final String SURGICAL_PROCEDURE_RESECTION_PARTIAL = "Resection - partial (part of your tumor was surgically removed)";

    private static final String SURGICAL_PROCEDURE_RESECTION_GROSS_TOTAL = "Resection - total"
            + " (all of your tumor was surgically removed)";

    private static final String SURGICAL_PROCEDURE_HAVE_NOT_HAD_BIOPSY_OR_RESECTION = "I have not had a biopsy "
            + "or a resection for my brain cancer";

    private static final String RADIATION_THERAPY_TYPE_FOCAL_RADIATION = "Focal radiation "
            + "(examples include CyberKnife, gamma knife radiosurgery, and stereotactic radiation)";
    private static final String RADIATION_THERAPY_TYPE_WHOLE_BRAIN_RADIATION_THERAPY = "Whole-brain radiation therapy (WBRT)";
    private static final String RADIATION_THERAPY_TYPE_CRANIOSPINAL_RADIATION = "Craniospinal radiation";
    private static final String RADIATION_THERAPY_TYPE_PROTON_BEAM_RADIATION_THERAPY = "Proton beam radiation therapy";

    private static final String BRAIN_CANCER_RESPONSE_HAVE_NOT_RECEIVED_ANY_MEDICATIONS = "I have not received any medications"
            + " for treatment of my brain cancer";

    private static final String BRAIN_CANCER_RESPONSE_DO_NOT_KNOW_THE_NAMES_OF_THE_MEDICATIONS = "I do not know"
            + " the names of the medications";

    private static final String BRAIN_CANCER_MEDICATION_WAS_PART_OF_A_CLINICAL_TRAIL = "This was part of a clinical trial";

    private static final String BRAIN_CANCER_ADD_ANOTHER_MEDICATION_THERAPY_BUTTON_TEXT = "+ Add another medication/therapy";


    //For email registration
    String testUserEmailAddress;
    String testUserPassword;

    //For new user creation
    String testUserFirstName;
    String testUserLastName;
    String testUserFullName;

    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        BrainWebsite.setDomain();
        return BrainAppSite.class;
    }

    @Before
    @Override
    public void background() {
        testUserEmailAddress = JDITestUtils.generateNewUserEmail();
        testUserPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);

        testUserFirstName = JDITestUtils.generateNewUserName(FIRST_NAME_DESCRIPTION);
        testUserLastName = JDITestUtils.generateNewUserName(LAST_NAME_DESCRIPTION);
        testUserFullName = JDITestUtils.getFullName(testUserFirstName, testUserLastName);

        //Get past the gatekeeper
        gatekeeperPage.verifyPageIsOpened();
        DatabaseUtility.unlockGatekeeperPage(gatekeeperPage);
        gatekeeperPage.clickSubmit();
    }

    @Test
    @Category(SmokeTests.class)
    @DisplayName("Nebula-Brain: I have been diagnosed with brain cancer")
    public void testIHaveBeenDiagnosedWithBrainCancer() {
        //Go to count me in
        homePage.verifyPageIsOpened();
        homePage.clickCountMeIn();

        //Fill out name and select "I have a loved one..." and then Submit
        countMeInPage.inputFirstName(testUserFirstName);
        countMeInPage.inputLastName(testUserLastName);
        countMeInPage.selectBrainCancerStatus(DIAGNOSED_WITH_BRAIN_CANCER);
        countMeInPage.clickSubmit();

        //Register
        auth0Page.verifyPageIsOpened();
        auth0Page.inputApplicationUserName(testUserEmailAddress);
        auth0Page.inputApplicationUserPassword(testUserPassword);
        auth0Page.clickApplicationSubmit();

        //Complete /about-you
        aboutYouPage.verifyPageIsOpened();

        // 1. When were you first diagnosed with brain cancer?
        aboutYouPage.selectMonthOfFirstDiagnosis(DIAGNOSIS_MONTH);
        aboutYouPage.selectYearOfFirstDiagnosis(DIAGNOSIS_YEAR);

        // 2. When you were first diagnosed with brain cancer, what type was it?
        aboutYouPage.inputTypeOfInitialCancerDiagnosis(BRAIN_CANCER_INITIAL_DIAGNOSIS);

        // 3. Have you had progression or recurrence of your brain cancer?
        aboutYouPage.selectGradeAtTimeOfDiagnosis(SECOND_GRADE);

        // 4.Since your diagnosis, has the type of your brain cancer changed?
        aboutYouPage.setHaveHadTypeOfBrainCancerChanged(RESPONSE_YES);

        // 4a - What type did your brain cancer change to and when?
        aboutYouPage.setOtherBrainCancerType(BRAIN_CANCER_SECONDARY_DIAGNOSIS);
        aboutYouPage.setYearOfOtherBrainCancerDiagnosis(DIAGNOSIS_YEAR_OF_OTHER_BRAIN_CANCER_TYPE);
        aboutYouPage.setMonthOfOtherBrainCancerDiagnosis(DIAGNOSIS_MONTH_OF_OTHER_BRAIN_CANCER_TYPE);

        //5. Since your diagnosis, has the grade of your brain cancer changed?
        aboutYouPage.setGradeOfBrainCancerWasChanged(RESPONSE_YES);
        
        //5a - What grade did your brain cancer change to and when?
        aboutYouPage.setGradeOfBrainCancerAfterChange(THIRD_GRADE);
        aboutYouPage.setYearOfBrainCancerGradeChange(DIAGNOSIS_YEAR_OF_OTHER_BRAIN_CANCER_TYPE);
        aboutYouPage.setMonthOfBrainCancerGradeChange(DIAGNOSIS_MONTH_OF_OTHER_BRAIN_CANCER_TYPE);

        // 6. In what year were you born?
        aboutYouPage.selectYearOfBirth(BIRTH_YEAR);

        // 7. What country do you live in?
        aboutYouPage.selectCountryOfResidence(COUNTRY_OF_RESIDENCE);

        // 8. What is your ZIP or postal code?
        aboutYouPage.inputZipcode(RESIDENTIAL_ZIPCODE);

        // 9. How did you hear about the project?
        aboutYouPage.inputReferralSource(METHOD_OF_LEARNING_ABOUT_BRAIN_CANCER_PROJECT);
        aboutYouPage.clickSubmit();

        //Complete /consent
        consentPage.verifyPageIsOpened();

        // At 1. Key Points
        consentPage.verifyGeneralConsentContentDisplayed();
        consentPage.clickNext();

        // At 2. Full Form
        consentPage.verifyGeneralConsentContentDisplayed();
        consentPage.clickNext();

        // At 3. Sign Consent
        consentPage.setAgreeToSampleOfBloodBeingDrawn(RESPONSE_YES);
        consentPage.setAgreeToStoredTissueSample(RESPONSE_NO);
        consentPage.inputSignature(testUserFullName);
        consentPage.setDateOfBirth(BIRTH_INFORMATION);
        consentPage.clickSubmit();

        // Complete /release-survey
        medicalReleaseFormPage.verifyPageIsOpened();

        medicalReleaseFormPage.setFullName(testUserFullName);
        medicalReleaseFormPage.setCountryOrUSTerritory(COUNTRY_UNITED_STATES);
        medicalReleaseFormPage.setMainStreetAddress(UNITED_STATES_STREET_ONE_ADDRESS);
        medicalReleaseFormPage.setOptionalStreetAddress(UNITED_STATES_STREET_TWO_ADDRESS);
        medicalReleaseFormPage.setCity(UNITED_STATES_CITY);
        medicalReleaseFormPage.setState(UNITED_STATES_STATE);
        medicalReleaseFormPage.setZipcode(UNITED_STATES_ZIPCODE);
        medicalReleaseFormPage.setPhoneNumber(PHONE_NUMBER);
        medicalReleaseFormPage.chooseToUseAsEnteredAddressOrSuggestedAddress(true);

        medicalReleaseFormPage.setPhysicianName(PHYSICIAN_NAME);
        medicalReleaseFormPage.setPhysicianInstitutionName(PHYSICIAN_INSTITUTION);
        medicalReleaseFormPage.setPhysicianInstitutionCity(PHYSICIAN_CITY);
        medicalReleaseFormPage.setPhysicianInstitutionState(PHYSICIAN_STATE);

        medicalReleaseFormPage.setInstitutionWhereInitialBiopsyWasPerformed(INITIAL_BIOPSY_INSTITUTION_NAME);
        medicalReleaseFormPage.setCityWhereInitialBiopsyWasPerformed(INITIAL_BIOPSY_INSTITUTION_CITY);
        medicalReleaseFormPage.setStateWhereInitialBiopsyPerformed(INITIAL_BIOPSY_INSTITUTION_STATE);

        medicalReleaseFormPage.clickAddAnotherInstitution();
        medicalReleaseFormPage.setInstitutionWhereAdditionalBiopsyOrSurgeryWasPerformed(ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_NAME);
        medicalReleaseFormPage.setCityWhereAdditionalBiopsyOrSurgeryWasPerformed(ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_CITY);
        medicalReleaseFormPage.setStateWhereAdditionalBiopsyOrSurgeryWasPerformed(ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_STATE);

        medicalReleaseFormPage.clickReleaseAgreementCheckbox();
        medicalReleaseFormPage.clickSubmit();

        //See post-consent survey
        postConsentSurveyPage.verifyPageIsOpened();

        postConsentSurveyPage.setSurgicalProcedure(SURGICAL_PROCEDURE_BIOPSY);
        postConsentSurveyPage.setSurgicalProcedure(SURGICAL_PROCEDURE_RESECTION_PARTIAL);
        postConsentSurveyPage.setSurgicalProcedure(SURGICAL_PROCEDURE_RESECTION_GROSS_TOTAL);

        postConsentSurveyPage.setHaveHadRadiationTreatmentforBrainCancer(RESPONSE_YES);
        postConsentSurveyPage.setRadiationTherapyReceived(RADIATION_THERAPY_TYPE_FOCAL_RADIATION);
        postConsentSurveyPage.setRadiationTherapyReceived(RADIATION_THERAPY_TYPE_CRANIOSPINAL_RADIATION);
        postConsentSurveyPage.setRadiationTherapyReceived(RADIATION_THERAPY_TYPE_WHOLE_BRAIN_RADIATION_THERAPY);
        postConsentSurveyPage.setRadiationTherapyReceived(RADIATION_THERAPY_TYPE_PROTON_BEAM_RADIATION_THERAPY);

        postConsentSurveyPage.setGender(GENDER_WOMAN);
        postConsentSurveyPage.setIsTransgender(RESPONSE_NO);
        postConsentSurveyPage.setRace(ETHNICITY_BLACK_OR_AFRICAN_AMERICAN);
        postConsentSurveyPage.inputExperienceWithBrainCancer(RESPONSE_UNSURE_HOW_TO_ANSWER);

        postConsentSurveyPage.clickSubmit();

        // See dashbaord
        dashboardPage.verifyPageIsOpened();
        dashboardPage.verifyDashboardAnnouncementMessageDisplayed();
        dashboardPage.closeAnnoucementMessage();
        dashboardPage.listAllActivitiesWithStatuses();

        //Go to homepage
    }

    @Test
    @Category(SmokeTests.class)
    @DisplayName("Nebula-Brain: I have not been diagnosed with brain cancer but want to stay informed")
    public void testIHaveNotBeenDiagnosedWithBrainCancerButWantToStayInformed() {
        //Go to /count-me-in
        homePage.verifyPageIsOpened();
        homePage.clickCountMeIn();

        //Fill out /count-me-in
        countMeInPage.verifyPageIsOpened();
        countMeInPage.inputFirstName(testUserFirstName);
        countMeInPage.inputLastName(testUserLastName);

        //Select the second option in order to generate 'Join Mailing List' modal
        countMeInPage.selectBrainCancerStatus(NOT_DIAGNOSED_WITH_BRAIN_CANCER_BUT_WANT_TO_STAY_INFORMED);
        countMeInPage.clickSubmit();

        //Verify first and last name are already on the modal once generated
        joinMailingList.verifyPageIsOpened();
        joinMailingList.verifyFirstNameWasSet(testUserFirstName);
        joinMailingList.verifyLastNameWasSet(testUserLastName);

        //Input email
        joinMailingList.setEmailAddress(testUserEmailAddress);
        joinMailingList.setConfirmedEmailAddress(testUserEmailAddress);

        //Submit modal
        joinMailingList.clickJoin();

        //Be reditected to /stay-informed
        stayInformedPage.verifyPageIsOpened();

        //Go to home page
        stayInformedPage.clickReturnHome();
    }

    @Override
    public void endTest() {

    }
}
