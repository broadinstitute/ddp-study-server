package org.broadinstitute.ddp.pages.angiopages;

import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LovedOnePage extends DDPPage {

    private static final Logger logger = LoggerFactory.getLogger(LovedOnePage.class);

    //============================ Loved one page xpaths        ==============================

    //Loved One introduction block
    private static final String INTRODUCTION_MESSAGE_BLOCK_XPATH = "(//div[contains(@class, 'ddp-content')])[1]";

    //Question 1 - 'What is your relation to your loved one?' dropdown list
    private static final String LOVED_ONE_RELATIONSHIP_TYPE_DROPDOWN_XPATH = "//li[@value = 1]//select";
    private static final String LOVED_ONE_RELATIONSHIP_TYPE_OTHER = "Other";
    private static final String LOVED_ONE_RELATIONSHIP_TYPE_OTHER_TEXTFIELD_XPATH = "//li[@value = 1]//input";

    //Question 2 - 'What is your loved one's first name?' textfield
    private static final String LOVED_ONE_FIRST_NAME_TEXTFIELD_XPATH = "//input[contains(@data-ddp-test, 'LOVEDONE_FIRST_NAME')]";

    //Question 3 - 'What is your loved one's last name?' textfield
    private static final String LOVED_ONE_LAST_NAME_TEXTFIELD_XPATH = "//input[contains(@data-ddp-test, 'LOVEDONE_LAST_NAME')]";

    //Question 4 - 'What is your loved one's date of birth? (MM-DD-YYYY)' date textfields
    private static final String LOVED_ONE_BIRTH_MONTH_TEXTFIELD_XPATH = "//li[@value=4]//ddp-date//input[contains(@placeholder, 'MM')]";
    private static final String LOVED_ONE_BIRTH_DAY_TEXTFIELD_XPATH = "//li[@value=4]//ddp-date//input[contains(@placeholder, 'DD')]";
    private static final String LOVED_ONE_BIRTH_YEAR_TEXTFIELD_XPATH = "//li[@value=4]//ddp-date//input[contains(@placeholder, 'YYYY')]";

    //Question 5 - 'What zip code did your loved one live in when diagnosed with angiosarcoma?' textfield
    private static final String LOVED_ONE_RESIDENCE_ZIPCODE_TEXTFIELD_XPATH = "//li[@value = 5]"
            + "//input[contains(@data-ddp-test, 'LOVEDONE_DIAGNOSIS_POSTAL_CODE')]";

    //Question 6 - 'What zip code did your loved one live in when they passed?' textfield
    private static final String LOVED_ONE_PLACE_OF_DEATH_ZIPCODE_TEXTFIELD_XPATH = "//li[@value = 6]"
            + "//input[contains(@data-ddp-test, 'LOVEDONE_PASSED_POSTAL_CODE')]";

    //Question 7 -  'When was your loved one diagnosed with angiosarcoma?' month and year dropdown lists
    private static final String LOVED_ONE_DIAGNOSIS_MONTH_DROPDOWN_XPATH = "(//li[@value = 7]//ddp-date//select)[1]";
    private static final String LOVED_ONE_DIAGNOSIS_YEAR_DROPDOWN_XPATH = "(//li[@value = 7]//ddp-date//select)[2]";

    //Question 8 -  'When did your loved one pass away?' month and year dropdown lists
    private static final String LOVED_ONE_MONTH_OF_PASSING_DROPDOWN_XPATH = "(//li[@value = 8]//ddp-date//select)[1]";
    private static final String LOVED_ONE_YEAR_OF_PASSING_DROPDOWN_XPATH = "(//li[@value = 8]//ddp-date//select)[2]";

    //Question 9 -  'When your loved one was first diagnosed with angiosarcoma, where in their body was it found?' picklist
    private static final String LOVED_ONE_INITIAL_DIAGNOSIS_LOCATION_PICKLIST_XPATH = "//li[@value = 9]"
            + "//ddp-activity-checkboxes-picklist-question//mat-list";

    //Question 9 - 'Please provide details' textfield for selecting Bone/Limb, Abdonmina Area, or Other
    private static final String LOVED_ONE_INITIAL_DIAGNOSIS_DETAILS_TEXTFIELD_XPATH = "(//li[@value = 9]"
            + "//input[contains(@placeholder, 'Please provide details')])";

    //Question 10 - 'Please list where else in their body your loved one's angiosarcoma spread.' picklist
    private static final String LOVED_ONE_ADDITIONAL_DIAGNOSIS_LOCATIONS_PICKLIST_XPATH = "//li[@value = 10]"
            + "//ddp-activity-checkboxes-picklist-question//mat-list";

    //Question 10 - Bone/Limb 'Please provide details'
    private static final String BONE_LIMB_ADDITIONAL_DETAILS_XPATH = "(//input[contains(@placeholder, 'Please provide details')])[2]";

    //Question 10 - Abdominal Area 'Please provide details'
    private static final String ABDOMINAL_AREA_ADDITIONAL_DETAILS_XPATH = "(//input[contains(@placeholder, 'Please provide details')])[3]";

    //Question 10 - Other 'Please provide details'
    private static final String OTHER_ADDITIONAL_DETAILS_XPATH = "(//input[contains(@placeholder, 'Please provide details')])[4]";

    //Question 11 main - 'Was your loved one ever diagnosed with another kind of cancer(s)?' picklist
    private static final String LOVED_ONE_DIAGNOSED_WITH_OTHER_CANCER_PICKLIST_XPATH = "(//li[@value = 11]"
            + "//ddp-activity-checkboxes-picklist-question//mat-list)[1]";

    //Question 11 (main's conditional) -  'Did your loved one ever have radiation as a treatment for their other cancer(s)?' picklist
    private static final String LOVED_ONE_RADIATION_TREATMENT_PICKLIST_XPATH = "(//li[@value = 11]"
            + "//ddp-activity-checkboxes-picklist-question//mat-list)[2]";

    //Question 11 (main's conditional) - 'Please describe in which part(s) of their body' textfield
    private static final String LOVED_ONE_INFORMATION_ABOUT_OTHER_CANCER_RADIATION_TREATMENT_TEXTFIELD_XPATH = "//li[@value=11]"
            + "//input[contains(@placeholder, 'Please describe in which part')]";

    //Question 11 (mains's conditional) - 'Please tell us more about your loved one's other cancer(s).' textfield
    private static final String LOVED_ONE_INFORMATION_ABOUT_OTHER_CANCERS = "//li[@value = 11]"
            + "//textarea[contains(@data-ddp-test, 'LOVEDONE_OTHER_CANCER_TEXT')]";

    //Question 12 - 'Please tell us anything you think is important for us to know about your loved one's experience with angiosarcoma.'
    //              textfield
    private static final String LOVED_ONE_ANGIOSARCOMA_EXPERIENCE_TEXTFIELD_XPATH = "//li[@value = 12]"
            + "//textarea[contains(@data-ddp-test, 'LOVEDONE_EXPERIENCE')]";

    //Question 13 - 'We are currently exploring ways to include medical records and tissue from loved ones in our studies,
    //               may we contact you in the future regarding this?' picklist
    private static final String LOVED_ONE_FUTURE_CONTACT_AGREEMENT_PICKLIST_XPATH = "(//li[@value = 13]"
            + "//ddp-activity-checkboxes-picklist-question//mat-list)[1]";

    //Submit button
    private static final String SUBMIT_BUTTON_XPATH = "//button[contains(text(), 'SUBMIT')]";

    //============================ Loved one page webelements     ==============================
    @FindBy(xpath = INTRODUCTION_MESSAGE_BLOCK_XPATH)
    private WebElement introductionMessageBlock;

    @FindBy(xpath = LOVED_ONE_RELATIONSHIP_TYPE_DROPDOWN_XPATH)
    private WebElement relationshipToPatientDropdownMenu;

    @FindBy(xpath = LOVED_ONE_RELATIONSHIP_TYPE_OTHER_TEXTFIELD_XPATH)
    private WebElement relationShipToPatientSpecificationTextfield;

    @FindBy(xpath = LOVED_ONE_FIRST_NAME_TEXTFIELD_XPATH)
    private WebElement firstNameTextfield;

    @FindBy(xpath = LOVED_ONE_LAST_NAME_TEXTFIELD_XPATH)
    private WebElement lastNameTextfield;

    @FindBy(xpath = LOVED_ONE_BIRTH_MONTH_TEXTFIELD_XPATH)
    private WebElement birthMonthTextfield;

    @FindBy(xpath = LOVED_ONE_BIRTH_DAY_TEXTFIELD_XPATH)
    private WebElement birthDayTextfield;

    @FindBy(xpath = LOVED_ONE_BIRTH_YEAR_TEXTFIELD_XPATH)
    private WebElement birthYearTextfield;

    @FindBy(xpath = LOVED_ONE_RESIDENCE_ZIPCODE_TEXTFIELD_XPATH)
    private WebElement zipcodeAtDiagnosis;

    @FindBy(xpath = LOVED_ONE_PLACE_OF_DEATH_ZIPCODE_TEXTFIELD_XPATH)
    private WebElement zipcodeAtPassing;

    @FindBy(xpath = LOVED_ONE_DIAGNOSIS_MONTH_DROPDOWN_XPATH)
    private WebElement diagnosisMonthDropdown;

    @FindBy(xpath = LOVED_ONE_DIAGNOSIS_YEAR_DROPDOWN_XPATH)
    private WebElement diagnosisYearDropdown;

    @FindBy(xpath = LOVED_ONE_MONTH_OF_PASSING_DROPDOWN_XPATH)
    private WebElement passingMonthDropdown;

    @FindBy(xpath = LOVED_ONE_YEAR_OF_PASSING_DROPDOWN_XPATH)
    private WebElement passingYearDropdown;

    @FindBy(xpath = LOVED_ONE_INITIAL_DIAGNOSIS_DETAILS_TEXTFIELD_XPATH)
    private WebElement initialDiagnosisLocationDetailsTextfield;

    @FindBy(xpath = BONE_LIMB_ADDITIONAL_DETAILS_XPATH)
    private WebElement boneLimbDetails;

    @FindBy(xpath = ABDOMINAL_AREA_ADDITIONAL_DETAILS_XPATH)
    private WebElement abdominalAreaDetails;

    @FindBy(xpath = OTHER_ADDITIONAL_DETAILS_XPATH)
    private WebElement otherDetails;

    @FindBy(xpath = LOVED_ONE_INFORMATION_ABOUT_OTHER_CANCER_RADIATION_TREATMENT_TEXTFIELD_XPATH)
    private WebElement otherCancerRadiationTreatmentInformationTextfield;

    @FindBy(xpath = LOVED_ONE_INFORMATION_ABOUT_OTHER_CANCERS)
    private WebElement informationAboutOtherCancerTextfield;

    @FindBy(xpath = LOVED_ONE_ANGIOSARCOMA_EXPERIENCE_TEXTFIELD_XPATH)
    private WebElement lovedOneExperience;

    @FindBy(xpath = SUBMIT_BUTTON_XPATH)
    private WebElement submit;

    //============================ Loved one page methods/actions ==============================
    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
        verifyIntroductionMessageDisplayed();
    }

    public void verifyIntroductionMessageDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(introductionMessageBlock));
        JDIPageUtils.scrollDownToElement(INTRODUCTION_MESSAGE_BLOCK_XPATH);
        Assert.assertTrue(introductionMessageBlock.isDisplayed());
    }

    private boolean hasOtherRelationshipToPatient(String relationshipStatus) {
        if (relationshipStatus.equalsIgnoreCase(LOVED_ONE_RELATIONSHIP_TYPE_OTHER)) {
            return true;
        }
        return false;
    }

    public void setRelationshipToPatient(String relationshipStatus, String relationshipDescription) {
        JDIPageUtils.scrollDownToElement(LOVED_ONE_RELATIONSHIP_TYPE_DROPDOWN_XPATH);
        Select relationshipDropdown = new Select(relationshipToPatientDropdownMenu);
        relationshipDropdown.selectByVisibleText(relationshipStatus);
        logger.info("User is the patient's: {}", relationshipStatus);

        if (hasOtherRelationshipToPatient(relationshipStatus)) {
            shortWait.until(ExpectedConditions.visibilityOf(relationShipToPatientSpecificationTextfield));
            JDIPageUtils.scrollDownToElement(LOVED_ONE_RELATIONSHIP_TYPE_OTHER_TEXTFIELD_XPATH);
            JDIPageUtils.inputUsingJavaScript(relationshipDescription, relationShipToPatientSpecificationTextfield);
            logger.info("User is specifically the patient's: {}", relationshipDescription);
        }
    }

    public void inputFirstName(String name) {
        shortWait.until(ExpectedConditions.visibilityOf(firstNameTextfield));
        JDIPageUtils.scrollDownToElement(LOVED_ONE_FIRST_NAME_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(name, firstNameTextfield);
    }

    public void inputLastName(String name) {
        shortWait.until(ExpectedConditions.visibilityOf(lastNameTextfield));
        JDIPageUtils.scrollDownToElement(LOVED_ONE_LAST_NAME_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(name, lastNameTextfield);
    }

    public void inputDateOfBirth(String birthInformation) {
        String month = JDIPageUtils.parseBirthdateInformation(birthInformation, MONTH);
        String day = JDIPageUtils.parseBirthdateInformation(birthInformation, DAY);
        String year = JDIPageUtils.parseBirthdateInformation(birthInformation, YEAR);

        setBirthMonth(month);
        setBirthDay(day);
        setBirthYear(year);
    }

    private void setBirthMonth(String month) {
        JDIPageUtils.scrollDownToElement(LOVED_ONE_BIRTH_MONTH_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(month, birthMonthTextfield);
    }

    private void setBirthDay(String day) {
        JDIPageUtils.scrollDownToElement(LOVED_ONE_BIRTH_DAY_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(day, birthDayTextfield);
    }

    private void setBirthYear(String year) {
        JDIPageUtils.scrollDownToElement(LOVED_ONE_BIRTH_YEAR_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(year, birthYearTextfield);
    }

    public void inputZipcodeAtDiagnosis(String zipcode) {
        JDIPageUtils.scrollDownToElement(LOVED_ONE_RESIDENCE_ZIPCODE_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(zipcode, zipcodeAtDiagnosis);
    }

    public void inputZipcodeAtDeath(String zipcode) {
        JDIPageUtils.scrollDownToElement(LOVED_ONE_PLACE_OF_DEATH_ZIPCODE_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(zipcode, zipcodeAtPassing);
    }

    public void setDateOfDiagnosis(String month, String year) {
        JDIPageUtils.scrollDownToElement(LOVED_ONE_DIAGNOSIS_MONTH_DROPDOWN_XPATH);
        Select monthDropdown = new Select(diagnosisMonthDropdown);
        Select yearDropdown = new Select(diagnosisYearDropdown);

        monthDropdown.selectByVisibleText(month);
        yearDropdown.selectByVisibleText(year);
    }

    public void setDateOfPassing(String month, String year) {
        JDIPageUtils.scrollDownToElement(LOVED_ONE_MONTH_OF_PASSING_DROPDOWN_XPATH);
        Select monthDropdown = new Select(passingMonthDropdown);
        Select yearDropdown = new Select(passingYearDropdown);

        monthDropdown.selectByVisibleText(month);
        yearDropdown.selectByVisibleText(year);
    }

    public void setLocationOfInititalDiagnosis(String location) {
        //Question 9
        String locationXPath = JDIPageUtils.getPicklistWebElementXPathByName(QUESTION_NINE, location, false);
        JDIPageUtils.scrollDownToElement(locationXPath);
        JDIPageUtils.clickUsingJavaScript(locationXPath, XPATH);
    }

    public void setLocationOfInitialDiagnosisDetails(String details) {
        //Qustion 9 - "Please provide details"
        shortWait.until(ExpectedConditions.visibilityOf(initialDiagnosisLocationDetailsTextfield));
        JDIPageUtils.scrollDownToElement(LOVED_ONE_INITIAL_DIAGNOSIS_DETAILS_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(details, initialDiagnosisLocationDetailsTextfield);
    }

    public void setLocationOfHistoricalDiagnosis(String location) {
        //Question 10
        String locationXPath = JDIPageUtils.getPicklistWebElementXPathByName(QUESTION_TEN, location, false);
        JDIPageUtils.scrollDownToElement(locationXPath);
        JDIPageUtils.clickUsingJavaScript(locationXPath, XPATH);
    }

    public void inputHisotricalDiagnosisBoneLimbDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(boneLimbDetails));
        JDIPageUtils.scrollDownToElement(BONE_LIMB_ADDITIONAL_DETAILS_XPATH);
        JDIPageUtils.inputUsingJavaScript(details, boneLimbDetails);
    }

    public void inputHisotricalDiagnosisAbdominalAreaDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(abdominalAreaDetails));
        JDIPageUtils.scrollDownToElement(ABDOMINAL_AREA_ADDITIONAL_DETAILS_XPATH);
        JDIPageUtils.inputUsingJavaScript(details, abdominalAreaDetails);
    }

    public void inputHisotricalDiagnosisOtherDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(otherDetails));
        JDIPageUtils.scrollDownToElement(OTHER_ADDITIONAL_DETAILS_XPATH);
        JDIPageUtils.inputUsingJavaScript(details, otherDetails);
    }

    public void setDiagnosedWithOtherCancer(String response) {
        //Question 11 - main
        String locationXPath = JDIPageUtils.getPicklistWebElementXPathByName(QUESTION_ELEVEN, response, false);
        JDIPageUtils.scrollDownToElement(locationXPath);
        JDIPageUtils.clickUsingJavaScript(locationXPath, XPATH);
    }

    public void setHaveHadRadiationAsTreatmentForOtherCancer(String response) {
        //Question 11- conditional
        String locationXPath = JDIPageUtils.getPicklistWebElementXPathByName(QUESTION_ELEVEN, response, true);
        WebElement conditional = getDriver().findElement(By.xpath(locationXPath));
        shortWait.until(ExpectedConditions.visibilityOf(conditional));
        JDIPageUtils.scrollDownToElement(locationXPath);
        JDIPageUtils.clickUsingJavaScript(locationXPath, XPATH);
    }

    /**
     * Use this to answer the textfield that appears after answering 'Yes' to
     * setHavehadRadiationAsTreatmentForOtherCancer(String response)
     * @param response User's response for the textfield
     */
    public void inputDetailedExplanationOfCancer(String response) {
        //Question 11 - conditional
        shortWait.until(ExpectedConditions.visibilityOf(otherCancerRadiationTreatmentInformationTextfield));
        JDIPageUtils.inputUsingJavaScript(response, otherCancerRadiationTreatmentInformationTextfield);
    }

    public void inputAdditioanlInformationAbouttherCancer(String response) {
        //Question 11 - conditional
        shortWait.until(ExpectedConditions.visibilityOf(informationAboutOtherCancerTextfield));
        JDIPageUtils.inputUsingJavaScript(response, informationAboutOtherCancerTextfield);
    }

    public void inputExperience(String response) {
        //Question 12
        JDIPageUtils.scrollDownToElement(LOVED_ONE_ANGIOSARCOMA_EXPERIENCE_TEXTFIELD_XPATH);
        JDIPageUtils.inputUsingJavaScript(response, lovedOneExperience);
    }

    public void setAgreementTobeContactedInFuture(String response) {
        //Question 13
        String locationXPath = JDIPageUtils.getPicklistWebElementXPathByName(QUESTION_THIRTEEN, response, false);
        JDIPageUtils.scrollDownToElement(locationXPath);
        JDIPageUtils.clickUsingJavaScript(locationXPath, XPATH);
    }

    public void clickSubmit() {
        logger.info("Clicking submit using this xpath: {}", SUBMIT_BUTTON_XPATH);
        shortWait.until(ExpectedConditions.visibilityOf(submit));
        JDIPageUtils.scrollDownToElement(SUBMIT_BUTTON_XPATH);
        JDIPageUtils.clickButtonUsingJDI(SUBMIT_BUTTON_XPATH, XPATH);
    }

}
