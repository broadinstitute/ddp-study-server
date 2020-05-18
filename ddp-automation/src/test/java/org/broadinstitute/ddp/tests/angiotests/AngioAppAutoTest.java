package org.broadinstitute.ddp.tests.angiotests;

import static org.broadinstitute.ddp.AngioAppSite.aboutYouPage;
import static org.broadinstitute.ddp.AngioAppSite.auth0Page;
import static org.broadinstitute.ddp.AngioAppSite.consentPage;
import static org.broadinstitute.ddp.AngioAppSite.countMeInPage;
import static org.broadinstitute.ddp.AngioAppSite.dashboardPage;
import static org.broadinstitute.ddp.AngioAppSite.gatekeeperPage;
import static org.broadinstitute.ddp.AngioAppSite.homePage;
import static org.broadinstitute.ddp.AngioAppSite.joinMailingList;
import static org.broadinstitute.ddp.AngioAppSite.lovedOnePage;
import static org.broadinstitute.ddp.AngioAppSite.lovedOneThankYouPage;
import static org.broadinstitute.ddp.AngioAppSite.medicalReleaseFormPage;
import static org.broadinstitute.ddp.AngioAppSite.stayInformedPage;
import static org.broadinstitute.ddp.AngioWebsite.environmentContainsGatekeeper;
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

public class AngioAppAutoTest extends BaseTest {
    private static final Logger logger = LoggerFactory.getLogger(AngioAppAutoTest.class);

    //Different diagnosed statuses [/count-me-in]
    private static final String DIAGNOSED_WITH_ANGIOSARCOMA = "diagnosed with angiosarcoma";
    private static final String NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED = "not diagnosed but want to stay informed";
    private static final String NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE = "not diagnosed but lost loved one";

    //About-You information [/prequalifier]
    private static final String DIAGNOSED_MONTH = "June";
    private static final String DIAGNOSED_YEAR = "2014";

    private static final String DIAGNOSIS_LOCATION_HEAD_FACE_NECK_BUT_NOT_SCALP = "Head/Face/Neck (not scalp)";
    private static final String DIAGNOSIS_LOCATION_SCALP = "Scalp";
    private static final String DIAGNOSIS_LOCATION_BREAST = "Breast";
    private static final String DIAGNOSIS_LOCATION_HEART = "Heart";
    private static final String DIAGNOSIS_LOCATION_LIVER = "Liver";
    private static final String DIAGNOSIS_LOCATION_SPLEEN = "Spleen";
    private static final String DIAGNOSIS_LOCATION_LUNG = "Lung";
    private static final String DIAGNOSIS_LOCATION_BRAIN = "Brain";
    private static final String DIAGNOSIS_LOCATION_LYMPH_NODES = "Lymph Nodes";
    private static final String DIAGNOSIS_LOCATION_BONE_LIMB = "Bone/Limb";
    private static final String DIAGNOSIS_LOCATION_BONE_LIMB_DETAILS = "Forearm";
    private static final String DIAGNOSIS_LOCATION_ABDOMINAL_AREA = "Abdominal Area";
    private static final String DIAGNOSIS_LOCATION_ABDOMINAL_AREA_DETAILS = "Near the stomach";
    private static final String DIAGNOSIS_LOCATION_OTHER = "Other";
    private static final String DIAGNOSIS_LOCATION_OTHER_DETAILS = "Skin";
    private static final String DIAGNOSIS_LOCATION_OTHER_DETAILS_GENERIC = "The doctor will be able to give more details";
    private static final String DIAGNOSIS_LOCATION_I_DONT_KNOW = "I don't know";
    private static final String DIAGNOSIS_LOCATION_NO_EVIDENCE_OF_DISEASE = "No Evidence of Disease";

    private static final String RESPONSE_YES = "Yes";
    private static final String RESPONSE_NO = "No";
    private static final String RESPONSE_I_DONT_KNOW = "I don\'t know";
    private static final String RESPONSE_BEFORE = "Before";
    private static final String RESPONSE_AFTER = "After";
    private static final String RESPONSE_BOTH = "Both";

    private static final String ALL_TREATMENTS = "Approximately 3 months of chemotherpay and 5 weeks of daily radiation therapy.";
    private static final String REFERRAL_SOURCE = "Doctor";
    private static final String EXPERIENCE_WITH_ANGIOSARCOMA = "N/A, was recently diagnosed";
    private static final String DISEASE_NAME = "Breast Cancer";
    private static final String DISEASE_DIAGNOSIS_YEAR = "2015";

    private static final String ETHNICITY_IS_HISPANIC_LATINO_SPANISH = "Yes";
    private static final String ETHNICITY_IS_NOT_HISPANIC_LATINX_SPANISH = "No";
    private static final String ETHNICITY_DO_NOT_KNOW_IF_AM_HISPANIC_LATINX_OR_SPANISH = "I don't know";

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

    private static final String YEAR_OF_BIRTH = "1990";
    private static final String COUNTRY_OF_RESIDENCE = "Puerto Rico";
    private static final String ZIP_OR_POSTAL_CODE = "02142";

    //Consent status
    private static final String BIRTH_INFORMATION = "11/25/1990";

    //Address information

    //U.S. Test Address
    private static final String COUNTRY_UNITED_STATES = "UNITED STATES";
    private static final String UNITED_STATES_STREET_ONE_ADDRESS = "418 South Water Street";
    private static final String UNITED_STATES_STREET_TWO_ADDRESS = "";
    private static final String UNITED_STATES_CITY = "Sparta";
    private static final String UNITED_STATES_STATE = "Wisconsin";
    private static final String UNITED_STATES_ZIPCODE = "54656";

    //Canada Test Address
    private static final String COUNTRY_CANADA = "CANADA";
    private static final String CANADA_STREET_ONE_ADDRESS = "18 Kenaston Gardens";
    private static final String CANADA_STREET_TWO_ADDRESS = "North York Apartment";
    private static final String CANADA_CITY = "Toronto";
    private static final String CANADA_PROVINCE = "ONTARIO";
    private static final String CANADA_POSTAL_CODE = "M2K 3C7";
    private static final String PHONE_NUMBER = "123-456-7890";
    //todo code smell here - reading it for using as parameter makes sense but reading as variable does not
    private static final Boolean USE_SUGGESTED_ADDRESS = true;
    private static final Boolean USE_AS_ENTERED_ADDRESS = false;

    //Physician information
    private static final String PHYSICIAN_NAME = "Doctor Nicolas Riveria";
    private static final String PHYSICIAN_INSTITUTION = "OSHU Hospital";
    private static final String PHYSICIAN_CITY = "Portland";
    private static final String PHYSICIAN_STATE = "Oregon";

    //Initial biopsy hospital/institution information
    private static final String INITIAL_BIOPSY_INSTITUTION_NAME = "UW Health University Hospital";
    private static final String INITIAL_BIOPSY_INSTITUTION_CITY = "Madison";
    private static final String INITIAL_BIOPSY_INSTITUTION_STATE = "Wisconsin";

    //Additional biopsies/surgeries information
    private static final String ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_NAME = "UCSF Medical Center";
    private static final String ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_CITY = "San Francisco";
    private static final String ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_STATE = "California";

    //Loved One activity information

    //Relationship to loved one
    private static final String RELATIONSHIP_TO_LOVED_ONE_PARENT = "Parent";
    private static final String RELATIONSHIP_TO_LOVED_ONE_SIBLING = "Sibling";
    private static final String RELATIONSHIP_TO_LOVED_ONE_CHILD = "Child";
    private static final String RELATIONSHIP_TO_LOVED_ONE_AUNT_OR_UNCLE = "Aunt/Uncle";
    private static final String RELATIONSHIP_TO_LOVED_ONE_SPOUSE = "Spouse";
    private static final String RELATIONSHIP_TO_LOVED_ONE_FRIEND = "Friend";
    private static final String RELATIONSHIP_TO_LOVED_ONE_OTHER = "Other";
    private static final String RELATIONSHIP_TO_LOVED_ONE_OTHER_SPECIFIC_DESCRIPTION = "Time Lord Companion";
    private static final String RELATIONSHIP_TO_LOVED_ONE_NO_SPECIFICS_NEEDED = "N/A";

    //Loved One's first name
    private static final String LOVED_ONE_FIRST_NAME = "Doctor";

    //Loved One's last name
    private static final String LOVED_ONE_LAST_NAME = "Who";

    //Loved One's Date of Birth
    private static final String LOVED_ONE_BIRTH_INFORMATION = "01/01/1900";

    //Loved One's zipcode while living
    private static final String LOVED_ONE_ZIPCODE_WHILE_ALIVE = "CF10 1JL";

    //Loved One's zipcode at moment of death
    private static final String LOVED_ONE_ZIPCODE_AT_MOMENT_OF_PASSING = "10119";

    //Loved One - month and year of angiosarcoma diagnosis
    private static final String LOVED_ONE_DIAGNOSIS_MONTH = "May";
    private static final String LOVED_ONE_DIAGNOSIS_YEAR = "2000";

    //Loved One - month and year of death due to angiosarcoma
    private static final String LOVED_ONE_MONTH_OF_PASSING = "October";
    private static final String LOVED_ONE_YEAR_OF_PASSING = "2001";

    //Loved One - radiation as treatment for their other cancer
    private static final String LOVED_ONE_RADIATION_INFORMATION = "I am unsure where.";

    //Loved One - more information about other cancer
    private static final String LOVED_ONE_EXPERIENCE_OF_OTHER_CANCER = "Previously had breast cancer.";

    //Loved One - experience
    private static final String LOVED_ONE_EXPERIENCE = "The doctor may be able to provide more information about the progression.";


    //For email registration
    String testUserEmailAddress;
    String testUserPassword;

    //For new user creation
    String testUserFirstName;
    String testUserLastName;
    String testUserFullName;

    @Override
    public Class<? extends DDPWebSite> getWebSite() {
        AngioWebsite.setDomain();
        return AngioAppSite.class;
    }

    @Before
    @Override
    public void background() {
        testUserEmailAddress = JDITestUtils.generateNewUserEmail();
        testUserPassword = CONFIG.getString(ConfigFile.TEST_USER_PASSWORD);

        testUserFirstName = JDITestUtils.generateNewUserName(FIRST_NAME_DESCRIPTION);
        testUserLastName = JDITestUtils.generateNewUserName(LAST_NAME_DESCRIPTION);
        testUserFullName = JDITestUtils.getFullName(testUserFirstName, testUserLastName);

        //Get past the gatekeeper - if there is one
        if (environmentContainsGatekeeper()) {
            gatekeeperPage.verifyPageIsOpened();
            DatabaseUtility.unlockGatekeeperPage(gatekeeperPage);
            gatekeeperPage.clickSubmit();
        }
    }

    @Test
    @Category(SmokeTests.class)
    @DisplayName("Pepper-Angio: I have been diagnosed... flow test")
    public void testAngiosarcomaIHaveBeenDiagnosed() {
        //Verify /ddp-angio is opened and get ready to register
        homePage.verifyPageIsOpened();
        homePage.clickCountMeIn();

        //Fill in first and last name for /count-me-in
        countMeInPage.verifyPageIsOpened();
        countMeInPage.inputFirstName(testUserFirstName);
        countMeInPage.inputLastName(testUserLastName);

        //Choose how test user relates to angiosarcoma
        countMeInPage.selectAngiosarcomaStatus(DIAGNOSED_WITH_ANGIOSARCOMA);
        countMeInPage.clickSubmit();

        //Register via auth0
        auth0Page.verifyPageIsOpened();
        auth0Page.inputApplicationUserName(testUserEmailAddress);
        auth0Page.inputApplicationUserPassword(testUserPassword);
        //waitSomeSeconds(5);
        auth0Page.clickApplicationSubmit();
        //DatabaseUtility.verifyUserExists();

        //Complete about-youl
        /*waitSomeSeconds(5);
        refreshPage();
        waitSomeSeconds(5);*/
        aboutYouPage.verifyPageIsOpened();

        //1. When were you first diagnosed with angiosarcoma?
        aboutYouPage.selectMonthOfFirstDiagnosis(DIAGNOSED_MONTH);
        aboutYouPage.selectYearOfFirstDiagnosis(DIAGNOSED_YEAR);

        //2. When you were first diagnosed with angiosarcoma, where in your body was it found (select all that apply)?
        aboutYouPage.selectLocationOfFirstDiagnosis(DIAGNOSIS_LOCATION_SCALP);

        aboutYouPage.selectLocationOfFirstDiagnosis(DIAGNOSIS_LOCATION_BONE_LIMB);
        aboutYouPage.inputFirstDiagnosisBoneLimbDetails(DIAGNOSIS_LOCATION_BONE_LIMB_DETAILS);

        aboutYouPage.selectLocationOfFirstDiagnosis(DIAGNOSIS_LOCATION_ABDOMINAL_AREA);
        aboutYouPage.inputFirstDiagnosisAbdominalAreaDetails(DIAGNOSIS_LOCATION_ABDOMINAL_AREA_DETAILS);

        aboutYouPage.selectLocationOfFirstDiagnosis(DIAGNOSIS_LOCATION_OTHER);
        aboutYouPage.inputFirstDiagnosisOtherDetails(DIAGNOSIS_LOCATION_OTHER_DETAILS);

        //3. Please select all of the places in your body that you have ever had angiosarcoma (select all that apply).
        aboutYouPage.selectLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_HEAD_FACE_NECK_BUT_NOT_SCALP);
        aboutYouPage.selectLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_SCALP);
        aboutYouPage.selectLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_LYMPH_NODES);

        aboutYouPage.selectLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_BONE_LIMB);
        aboutYouPage.inputHistoricalDiagnosisBoneLimbDetails(DIAGNOSIS_LOCATION_BONE_LIMB_DETAILS);

        aboutYouPage.selectLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_ABDOMINAL_AREA);
        aboutYouPage.inputHistoricalDiagnosisAbdominalAreaDetails(DIAGNOSIS_LOCATION_ABDOMINAL_AREA_DETAILS);

        aboutYouPage.selectLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_OTHER);
        aboutYouPage.inputHistoricalDiagnosisOtherDetails(DIAGNOSIS_LOCATION_OTHER_DETAILS);

        //4. Please select all of the places in your body where you currently have angiosarcoma (select all that apply).
        //   If you don’t have evidence of disease, please select "No Evidence of Disease (NED)".
        aboutYouPage.selectLocationOfCurrentDiagnosis(DIAGNOSIS_LOCATION_LYMPH_NODES);

        //5. Have you had surgery to remove angiosarcoma?
        //5.1 If so, did the surgery remove all known cancer tissue (also known as "clean margins")?
        aboutYouPage.selectHaveHadSurgeryToRemoveAngiosarcoma(RESPONSE_YES);
        aboutYouPage.selectSurgeryRemovedAllKnownCancerTissue(RESPONSE_YES);

        //6. Have you had radiation as a treatment for angiosarcoma?
        //   If you had radiation for other cancers, we will ask you about that later.
        //6.1 Was your radiation before or after surgery?
        aboutYouPage.setHaveHadRadiationAsTreatmentForAngiosarcoma(RESPONSE_YES);
        aboutYouPage.setRadiationBeforeOrAfterSugery(RESPONSE_AFTER);

        //7. Please list the medications, drugs, and chemotherapies you have been prescribed specifically for the treatment of angiosarcoma.
        //   It's okay if there are treatments you don't remember.
        aboutYouPage.setTreatmentsPrescribed(ALL_TREATMENTS);

        //8. Are you currently being treated for your angiosarcoma?
        //8.1 Please list the therapies you are currently receiving for angiosarcoma
        //   (this can include upcoming surgeries, radiation, or medications, drugs, or chemotherapies).
        aboutYouPage.setCurrentlyBeingTreatedForAngiosarcoma(RESPONSE_YES);
        aboutYouPage.inputAllTherapiesCurrentlyRecievingForAngiosarcoma(ALL_TREATMENTS);

        //9. Were you ever diagnosed with any other kind of cancer(s)?
        //9.1 Please list which cancer(s) and approximate year(s) of diagnosis.
        aboutYouPage.setHaveBeenDiagnosedWithDifferentCancer(RESPONSE_YES);
        //aboutYouPage.inputDiseaseNameAndYearOfDiagnosis(DISEASE_NAME, DISEASE_DIAGNOSIS_YEAR);

        //10. Have you had radiation as a treatment for another cancer(s)?
        //10.1 In what part of your body did you receive radiation for your other cancer(s)?
        aboutYouPage.setHadRadiationAsTreatmentForAnotherCancer(RESPONSE_YES);
        aboutYouPage.inputLocationOfRadioationForOtherCancer(DIAGNOSIS_LOCATION_ABDOMINAL_AREA);

        //11. How did you hear about The Angiosarcoma Project?
        aboutYouPage.inputReferralSource(REFERRAL_SOURCE);

        //12. Optional: Tell us anything else you want about yourself and your experience with angiosarcoma.
        //    We are asking this so you have an opportunity to tell us things that you feel are important
        //    for our understanding of this disease.
        aboutYouPage.inputExperienceWithAngiosarcomaOptionalInput(EXPERIENCE_WITH_ANGIOSARCOMA);

        //13. Do you consider yourself Hispanic, Latino/a or Spanish?
        aboutYouPage.setUserEthnicityAsHispanicLatinxOrSpanish(ETHNICITY_IS_HISPANIC_LATINO_SPANISH);

        //14. What is your race (select all that apply)?
        aboutYouPage.setUserEthnicityAsEtCetera(ETHNICITY_BLACK_OR_AFRICAN_AMERICAN);
        aboutYouPage.setUserEthnicityAsEtCetera(ETHNICITY_OTHER);
        aboutYouPage.setEthnicityOtherDetails(ETHNICITY_OTHER_DETAILS);

        //15. In what year were you born?
        aboutYouPage.setBirthYear(YEAR_OF_BIRTH);

        //16. What country do you live in?
        aboutYouPage.setCountryOfResidence(COUNTRY_OF_RESIDENCE);

        //17. What is your ZIP or postal code?
        aboutYouPage.inputZipcode(ZIP_OR_POSTAL_CODE);

        aboutYouPage.clickSubmit();

        //Complete consent
        consentPage.verifyPageIsOpened();

        //1. Key Points
        consentPage.verifyGeneralConsentContentDisplayed();
        consentPage.verifyDateConsentLastUpdatedDisplayed();
        consentPage.clickNext();

        //2. Full Form
        consentPage.verifyGeneralConsentContentDisplayed();
        consentPage.verifyDateConsentLastUpdatedDisplayed();
        consentPage.clickNext();

        //3. Sign Consent
        consentPage.setAgreeToSampleOfBloodBeingDrawn(RESPONSE_YES);
        consentPage.setAgreeToStoredTissueSample(RESPONSE_YES);
        consentPage.inputSignature(testUserFullName);
        consentPage.setDateOfBirth(BIRTH_INFORMATION);

        consentPage.clickSubmit();

        //Complete medical release form
        medicalReleaseFormPage.verifyPageIsOpened();

        //1. Your Contact Information
        medicalReleaseFormPage.setFullName(testUserFullName);
        medicalReleaseFormPage.setCountryOrUSTerritory(COUNTRY_UNITED_STATES);
        medicalReleaseFormPage.setMainStreetAddress(UNITED_STATES_STREET_ONE_ADDRESS);
        medicalReleaseFormPage.setOptionalStreetAddress(UNITED_STATES_STREET_TWO_ADDRESS);
        medicalReleaseFormPage.setCity(UNITED_STATES_CITY);
        medicalReleaseFormPage.setState(UNITED_STATES_STATE);
        medicalReleaseFormPage.setZipcode(UNITED_STATES_ZIPCODE);
        medicalReleaseFormPage.setPhoneNumber(PHONE_NUMBER); 
        medicalReleaseFormPage.verifyAddressSuggestionBoxDisplayed();
        medicalReleaseFormPage.chooseToUseAsEnteredAddressOrSuggestedAddress(USE_SUGGESTED_ADDRESS);

        //2. Your Physicians' Name
        medicalReleaseFormPage.setPhysicianName(PHYSICIAN_NAME);
        medicalReleaseFormPage.setPhysicianInstitutionName(PHYSICIAN_INSTITUTION);
        medicalReleaseFormPage.setPhysicianInstitutionCity(PHYSICIAN_CITY);
        medicalReleaseFormPage.setPhysicianInstitutionState(PHYSICIAN_STATE);

        //3. Your Hospital/Institution
        //Where was your initial biopsy for angiosarcoma performed?
        medicalReleaseFormPage.setInstitutionWhereInitialBiopsyWasPerformed(INITIAL_BIOPSY_INSTITUTION_NAME);
        medicalReleaseFormPage.setCityWhereInitialBiopsyWasPerformed(INITIAL_BIOPSY_INSTITUTION_CITY);
        medicalReleaseFormPage.setStateWhereInitialBiopsyPerformed(INITIAL_BIOPSY_INSTITUTION_STATE);

        //4. Where were any other biopsies or surgeries for your angiosarcoma performed?
        medicalReleaseFormPage.clickAddAnotherInstitution();
        medicalReleaseFormPage.setInstitutionWhereAdditionalBiopsyOrSurgeryWasPerformed(ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_NAME);
        medicalReleaseFormPage.setCityWhereAdditionalBiopsyOrSurgeryWasPerformed(ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_CITY);
        medicalReleaseFormPage.setStateWhereAdditionalBiopsyOrSurgeryWasPerformed(ADDITIONAL_BIOPSIES_SURGERIES_INSTITUTION_STATE);

        //Release agreement: "By completing this information, you are agreeing to allow us to contact..."
        medicalReleaseFormPage.clickReleaseAgreementCheckbox();
        medicalReleaseFormPage.clickSubmit();

        //Make sure the dashboard content is displayed
        dashboardPage.verifyPageIsOpened();
        dashboardPage.verifyDashboardAnnouncementMessageDisplayed();
        dashboardPage.closeAnnoucementMessage();
        dashboardPage.waitUntilDashboardContentDisplayed();
        dashboardPage.verifyDashboardDisplayed();
        dashboardPage.listAllActivitiesWithStatuses();
    }

    @Test
    @Category(SmokeTests.class)
    @DisplayName("Pepper-Angio: I have not been diagnosed...but want to stay informed flow test")
    public void testAngiosarcomaIHaveNotBeenDiagnosedButWantToStayInformed() {
        //Go to count me in
        homePage.verifyPageIsOpened();
        homePage.clickCountMeIn();

        //Fill out name and select "...want to stay informed..." and then "submit"
        countMeInPage.verifyPageIsOpened();
        countMeInPage.inputFirstName(testUserFirstName);
        countMeInPage.inputLastName(testUserLastName);
        countMeInPage.selectAngiosarcomaStatus(NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED);
        countMeInPage.clickSubmit();

        //Fill out Join Mailing List form
        joinMailingList.verifyPageIsOpened();

        //Check the Join Mailing List captures the names already entered in the count-me-in form
        joinMailingList.verifyFirstNameWasSet(testUserFirstName);
        joinMailingList.verifyLastNameWasSet(testUserLastName);
        joinMailingList.setEmailAddress(testUserEmailAddress);
        joinMailingList.setConfirmedEmailAddress(testUserEmailAddress);
        joinMailingList.clickJoin();

        //Check database to make sure user has joined
        //DatabaseUtility.checkUserSignedUpForMailingList(testUserEmailAddress);

        //Be redirected to stay informed page
        stayInformedPage.verifyPageIsOpened();
        stayInformedPage.clickReturnHome();

        //Check clicking [Return Home] leads back to homepage
        homePage.verifyPageIsOpened();
    }

    @Test
    @Category(SmokeTests.class)
    @DisplayName("Pepper-Angio: I have a loved one that has passed away from angiosarcoma")
    public void testAngiosarcomaIHaveLostALovedOne() {
        //Go to count me in
        homePage.verifyPageIsOpened();
        homePage.clickCountMeIn();

        //Fill out name and select "I have a loved one..." and then Submit
        countMeInPage.verifyPageIsOpened();
        countMeInPage.inputFirstName(testUserFirstName);
        countMeInPage.inputLastName(testUserLastName);
        countMeInPage.selectAngiosarcomaStatus(NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE);
        countMeInPage.clickSubmit();

        //Register
        auth0Page.verifyPageIsOpened();
        auth0Page.inputApplicationUserName(testUserEmailAddress);
        auth0Page.inputApplicationUserPassword(testUserPassword);
        auth0Page.clickApplicationSubmit();

        //Verify navigation to Lvoed One activity
        lovedOnePage.verifyPageIsOpened();

        //Answer Loved One activity questions

        //1. What is your relation to your loved one?
        lovedOnePage.setRelationshipToPatient(RELATIONSHIP_TO_LOVED_ONE_OTHER, RELATIONSHIP_TO_LOVED_ONE_OTHER_SPECIFIC_DESCRIPTION);

        //2. What is your loved one's first name?
        lovedOnePage.inputFirstName(LOVED_ONE_FIRST_NAME);

        //3. What is your loved one's last name?
        lovedOnePage.inputLastName(LOVED_ONE_LAST_NAME);

        //4. What is your loved one's date of birth? (MM-DD-YYYY)
        lovedOnePage.inputDateOfBirth(LOVED_ONE_BIRTH_INFORMATION);

        //5. What zip code did your loved one live in when diagnosed with angiosarcoma?
        lovedOnePage.inputZipcodeAtDiagnosis(LOVED_ONE_ZIPCODE_WHILE_ALIVE);

        //6. What zip code did your loved one live in when they passed?
        lovedOnePage.inputZipcodeAtDeath(LOVED_ONE_ZIPCODE_AT_MOMENT_OF_PASSING);

        //7. When was your loved one diagnosed with angiosarcoma?
        lovedOnePage.setDateOfDiagnosis(LOVED_ONE_DIAGNOSIS_MONTH, LOVED_ONE_DIAGNOSIS_YEAR);

        //8. When did your loved one pass away?
        lovedOnePage.setDateOfPassing(LOVED_ONE_MONTH_OF_PASSING, LOVED_ONE_YEAR_OF_PASSING);

        //9. When your loved one was first diagnosed with angiosarcoma, where in their body was it found?
        lovedOnePage.setLocationOfInititalDiagnosis(DIAGNOSIS_LOCATION_BONE_LIMB);
        lovedOnePage.setLocationOfInitialDiagnosisDetails(DIAGNOSIS_LOCATION_OTHER_DETAILS_GENERIC);

        //10. Please list where else in their body your loved one's angiosarcoma spread.
        lovedOnePage.setLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_HEAD_FACE_NECK_BUT_NOT_SCALP);
        lovedOnePage.setLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_SCALP);
        lovedOnePage.setLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_BONE_LIMB);
        lovedOnePage.inputHisotricalDiagnosisBoneLimbDetails(DIAGNOSIS_LOCATION_OTHER_DETAILS_GENERIC);

        lovedOnePage.setLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_ABDOMINAL_AREA);
        lovedOnePage.inputHisotricalDiagnosisAbdominalAreaDetails(DIAGNOSIS_LOCATION_OTHER_DETAILS_GENERIC);

        lovedOnePage.setLocationOfHistoricalDiagnosis(DIAGNOSIS_LOCATION_OTHER);
        lovedOnePage.inputHisotricalDiagnosisOtherDetails(DIAGNOSIS_LOCATION_OTHER_DETAILS_GENERIC);

        //11. Was your loved one ever diagnosed with another kind of cancer(s)?
        lovedOnePage.setDiagnosedWithOtherCancer(RESPONSE_YES);
        lovedOnePage.setHaveHadRadiationAsTreatmentForOtherCancer(RESPONSE_YES);

        //12. Please tell us anything you think is important for us to know about your loved one's experience with angiosarcoma.
        lovedOnePage.inputExperience(LOVED_ONE_EXPERIENCE);

        //13. We are currently exploring ways to include medical records and tissue from loved ones in our studies,
        //    may we contact you in the future regarding this?
        lovedOnePage.setAgreementTobeContactedInFuture(RESPONSE_NO);

        //Submit Loved One activity
        lovedOnePage.clickSubmit();

        //Verify navigation to Thank You Page
        lovedOneThankYouPage.verifyPageIsOpened();
        lovedOneThankYouPage.verifyHeaderDisplayed();
        lovedOneThankYouPage.verifyThankYouMessageDisplayed();

        //Navigate to dashboard
        lovedOneThankYouPage.clickUserMenuOption();
        lovedOneThankYouPage.clickDashboardOption();

        //Verify navigation to the dashboard
        dashboardPage.verifyPageIsOpened();
        dashboardPage.waitUntilDashboardContentDisplayed();
        dashboardPage.verifyDashboardDisplayed();
        dashboardPage.listAllActivitiesWithStatuses();
    }

    @Override
    public void endTest() {


    }

}
