package org.broadinstitute.ddp.pages.angiopages;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByText;
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

/**
 * The About-You page (currently has the url /prequalifier)
 **/
public class AboutYouPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(AboutYouPage.class);

    //General Content
    private static final String GENERAL_PAGE_CONTENT = "//article[@class='PageContent']";

    //Introduction text box
    private static final String INTRODUCTION_TEXT_BOX_XPATH = "//div[@class='PageContent-box']";

    //First diagnosed with angiosarcoma date (in month + year format)
    private static final String FIRST_DIAGNOSED_DATE_CONTENT_XPATH = "//li[@value=1]/parent::ol";
    private static final String FIRST_DIAGNOSED_DATE_MONTH_XPATH = "//option[contains(text(), 'Choose month...')]//parent::select";
    private static final String FIRST_DIAGNOSED_DATE_YEAR_XPATH = "(//option[contains(text(), 'Choose year...')]//parent::select)[1]";

    //Location(s) in the body of the first diagnosis of angiosarcoma checklist
    private static final String ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_CHECKLIST_XPATH = "(//ddp-activity-checkboxes-picklist-question"
            + "//mat-list)[1]";

    private static final String ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_BONE_LIMB_DETAILS = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[1]";
    private static final String ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_ABDOMINAL_AREA_DETAILS = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[2]";
    private static final String ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_OTHER_DETAILS = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[3]";

    private static final String ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_SPECIFIER = "[1]";

    //Location(s) in the body of any diagnosis of angiosarcoma checklist
    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_CHECKLIST_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[2]";

    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_BONE_LIMB_DETAILS = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[4]";
    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_ABDOMINAL_AREA = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[5]";
    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_OTHER_DETAILS = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[6]";

    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_SPECIFIER = "[2]";

    //Location(s) of current diagnosis of angiosarcoma in the body
    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_CHECKLIST_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[3]";

    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_BONE_LIMB_DETAILS = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[7]";
    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_ABDOMINAL_AREA_DETAILS = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[8]";
    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_OTHER_DETAILS = ""
            + "(//input[contains(@placeholder, 'Please provide details')])[9]";

    private static final String ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_SPECIFIER = "[3]";

    //Angiosarcoma treatment infobox
    private static final String ANGIOSARCOMA_TREATMENT_INFORMATION_TEXT_XPATH = "//p[contains(text(), 'radiation, and any medications')]";

    //Have had surgery to remove angiosarcoma
    private static final String HAVE_HAD_SURGERY_TO_REMOVE_ANGIOSARCOMA_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[4]";

    //Did the surgery remove all known cancer tissue a.k.a clean margins
    private static final String ALL_KNOWN_CANCER_TISSUE_REMOVED_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[5]";

    //Have had radiation as treatment for angiosarcoma
    private static final String ANGIOSARCOMA_USED_RADATION_FOR_TREATMENT_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[6]";

    //Had radiation before or after surgery
    private static final String ANGIOSARCOMA_HAD_RADIATION_BEFORE_AFTER_SURGERY_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[7]";

    //Angiosarcoma treatment text input
    private static final String ALL_USED_TREATMENTS_TEXT_INPUT_XPATH = "//textarea[contains(@data-ddp-test, 'answer:ALL_TREATMENTS')]";

    //Currently being treated for angiosarcoma
    private static final String CURRENTLY_BEING_TREATED_FOR_ANGIOSARCOMA_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[8]";

    //All therapies currently receving for angiosarcoma
    private static final String ALL_THERAPIES_CURRENTLY_USING_FOR_ANGIOSARCOMA_XPATH = "//*[contains(@data-ddp-test, 'CURRENT_THERAPIES')]";

    //Have been diagnosed with other cancers
    private static final String OTHER_CANCER_HAVE_BEEN_DIAGNOSED_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[9]";

    //Other cancer name(s)
    private static final String OTHER_CANCER_DISEASE_NAME_XPATH = "//*[contains(@data-ddp-test, 'OTHER_CANCER_LIST_NAME')]";

    //Other cancer diagnosis year(s)
    private static final String OTHER_CANCER_YEAR_OF_DIAGNOSIS_XPATH = "//ddp-activity-composite-answer//input[@placeholder='Year']";

    //Add another Cancer button
    private static final String ADD_ANOTHER_CANCER_BUTTON_TEXT = "ADD ANOTHER CANCER";

    //Have had radiation as treatment for another cancer(s)
    private static final String OTHER_CANCER_RECEIVED_RADATION_XPATH = ""
            + "(//ddp-activity-checkboxes-picklist-question//mat-list)[10]";

    //Location of radiation treatment for other cancer
    private static final String OTHER_CANCER_LOCATION_OF_RADIATION_TREATMENT_XPATH = ""
            + "//*[contains(@data-ddp-test, 'OTHER_CANCER_RADIATION_LOC')]";

    //'How did you hear about The Angiosarcoma Project' text input
    private static final String ANGIOSARCOMA_HOW_HEARD_ABOUT_PROJECT_XPATH = "//textarea[contains(@data-ddp-test, 'REFERRAL_SOURCE')]";

    //Experience with angiosarcoma text input
    private static final String ANGIOSARCOMA_EXPERIENCE_OPTIONAL_TEXT_INPUT_XPATH = "//textarea[contains(@data-ddp-test, 'EXPERIENCE')]";

    //Ethnicity [Hispanic, Latina/o, Spanish] checklist
    private static final String ETHNICITY_HISPANIC_LATINX_SPANISH_CONTENT_XPATH = "(//ddp-activity-checkboxes-picklist-question"
            + "//mat-list)[9]";
    private static final String ETHNICITY_HISPANIC_LATINX_SPANISH_CHECKLIST_XPATH = "(//ddp-activity-checkboxes-picklist-question"
            + "//mat-list)[4]";

    //Ethnicity (everybody else) checklist
    private static final String ETHNICITY_ET_CETERA_CONTENT_XPATH = "(//ddp-activity-checkboxes-picklist-question//mat-list)[10]";
    private static final String ETHNICITY_OTHER_DETAILS = "//li[@value=14]//input[contains(@placeholder, 'Please provide details')]";

    //Year of birth dropdown list
    private static final String YEAR_OF_BIRTH_XPATH = "(//option[contains(text(), 'Choose year...')]//parent::select)[2]";

    //Country of residence dropdown list
    private static final String COUNTRY_OF_RESIDENCE_XPATH = "//option[contains(text(), 'Choose country...')]//parent::select";

    //Zip or postal code text input
    private static final String ZIP_OR_POSTAL_CODE_XPATH = "//*[contains(@data-ddp-test, 'POSTAL_CODE')]";

    //User understand's storing of information text
    private static final String AGREEMENT_AND_UNDERSTANDING_OF_STORED_INFORMATION_XPATH = "//p[contains(text(), 'secure database')]";

    //Submit button
    private static final String SUBMIT_BUTTON_XPATH = "//button[contains(text(), 'SUBMIT')]";

    private static final String CHECKBOX_LABEL_OTHER = "Other";

    //Question numbers, used to get which one to answer
    private static final String QUESTION_ONE = "1";
    private static final String QUESTION_TWO = "2";
    private static final String QUESTION_THREE = "3";
    private static final String QUESTION_FOUR = "4";
    private static final String QUESTION_FIVE = "5";
    private static final String QUESTION_SIX = "6";
    private static final String QUESTION_SEVEN = "7";
    private static final String QUESTION_EIGHT = "8";
    private static final String QUESTION_NINE = "9";
    private static final String QUESTION_TEN = "10";
    private static final String QUESTION_ELEVEN = "11";
    private static final String QUESTION_TWELVE = "12";
    private static final String QUESTION_THIRTEEN = "13";
    private static final String QUESTION_FOURTEEN = "14";
    private static final String QUESTION_FIFTEEN = "15";
    private static final String QUESTION_SIXTEEN = "16";
    private static final String QUESTION_SEVENTEEN = "17";
    private static final String CONDITIONAL_SPECIFIER = "[2]";



    @FindBy(xpath = GENERAL_PAGE_CONTENT)
    private WebElement generalContent;

    @FindBy(xpath = INTRODUCTION_TEXT_BOX_XPATH)
    private WebElement introductionTextBox;

    @FindBy(xpath = FIRST_DIAGNOSED_DATE_CONTENT_XPATH)
    private WebElement diagnosedDateContent;

    @FindBy(xpath = FIRST_DIAGNOSED_DATE_MONTH_XPATH)
    private WebElement firstDiagnosedMonth;

    @FindBy(xpath = FIRST_DIAGNOSED_DATE_YEAR_XPATH)
    private WebElement firstDiagnosedYear;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_CHECKLIST_XPATH)
    private WebElement locationsOfFirstDiagnosisList;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_BONE_LIMB_DETAILS)
    private WebElement firstDiagnosisBoneLimbDetails;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_ABDOMINAL_AREA_DETAILS)
    private WebElement firstDiagnosisAbdominalAreaDetails;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_FIRST_LOCATIONS_OTHER_DETAILS)
    private WebElement firstDiagnosisOtherDetails;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_CHECKLIST_XPATH)
    private WebElement locationsOfHistoricalDiagnosisList;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_BONE_LIMB_DETAILS)
    private WebElement historicalDiagnosisBoneLimbDetails;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_ABDOMINAL_AREA)
    private WebElement historicalDiagnosisAbdominalAreaDetails;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_ALL_HISTORICAL_LOCATIONS_OTHER_DETAILS)
    private WebElement historicalDiagnosisOtherDetails;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_CHECKLIST_XPATH)
    private WebElement locationsOfCurrentDiagnosisList;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_BONE_LIMB_DETAILS)
    private WebElement currentDiagnosisBoneLimbDetails;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_ABDOMINAL_AREA_DETAILS)
    private WebElement currentDiagnosisAbdominalAreaDetails;

    @FindBy(xpath = ANGIOSARCOMA_DIAGNOSIS_ALL_CURRENT_LOCATIONS_OTHER_DETAILS)
    private WebElement currentDiagnosisOtherDetails;

    @FindBy(xpath = ANGIOSARCOMA_TREATMENT_INFORMATION_TEXT_XPATH)
    private WebElement requestOfTreatmentInformationText;

    @FindBy(xpath = HAVE_HAD_SURGERY_TO_REMOVE_ANGIOSARCOMA_XPATH)
    private WebElement haveHadSurgeryToRemoveAngiosarcoma;

    @FindBy(xpath = ALL_KNOWN_CANCER_TISSUE_REMOVED_XPATH)
    private WebElement surgeryRemovedAllCancerTissue;

    @FindBy(xpath = ANGIOSARCOMA_USED_RADATION_FOR_TREATMENT_XPATH)
    private WebElement haveTreatedAngiosarcomaWithRadiation;

    @FindBy(xpath = ANGIOSARCOMA_HAD_RADIATION_BEFORE_AFTER_SURGERY_XPATH)
    private WebElement treatedAngiosarcomaBeforeAfterSurgery;

    @FindBy(xpath = ALL_USED_TREATMENTS_TEXT_INPUT_XPATH)
    private WebElement allUsedTreatments;

    @FindBy(xpath = CURRENTLY_BEING_TREATED_FOR_ANGIOSARCOMA_XPATH)
    private WebElement currentlyBeingTreatedForAngiosarcoma;

    @FindBy(xpath = ALL_THERAPIES_CURRENTLY_USING_FOR_ANGIOSARCOMA_XPATH)
    private WebElement allTherapiesUsedForAngiosarcoma;

    @FindBy(xpath = OTHER_CANCER_HAVE_BEEN_DIAGNOSED_XPATH)
    private WebElement haveBeenDiagnosedWithOtherCancer;

    @FindBy(xpath = OTHER_CANCER_DISEASE_NAME_XPATH)
    private WebElement otherCancerDiseaseName;

    @FindBy(xpath = OTHER_CANCER_YEAR_OF_DIAGNOSIS_XPATH)
    private WebElement otherCancerDiagnosisYear;

    @ByText(ADD_ANOTHER_CANCER_BUTTON_TEXT)
    private WebElement addAnotherCancer;

    @FindBy(xpath = OTHER_CANCER_RECEIVED_RADATION_XPATH)
    private WebElement receivedRadiationForOtherCancer;

    @FindBy(xpath = OTHER_CANCER_LOCATION_OF_RADIATION_TREATMENT_XPATH)
    private WebElement locationOfRadiationTreatmentForOtherCancer;

    @FindBy(xpath = ANGIOSARCOMA_HOW_HEARD_ABOUT_PROJECT_XPATH)
    private WebElement referralSource;

    @FindBy(xpath = ANGIOSARCOMA_EXPERIENCE_OPTIONAL_TEXT_INPUT_XPATH)
    private WebElement angiosarcomaExperienceOptionalInput;

    @FindBy(xpath = ETHNICITY_HISPANIC_LATINX_SPANISH_CONTENT_XPATH)
    private WebElement ethnicityHispanicLatinxSpanishContent;

    @FindBy(xpath = ETHNICITY_HISPANIC_LATINX_SPANISH_CHECKLIST_XPATH)
    private WebElement ethnicityHispanicLatinxSpanish;

    @FindBy(xpath = ETHNICITY_ET_CETERA_CONTENT_XPATH)
    private WebElement ethnicityEtCeteraContent;

    @FindBy(xpath = ETHNICITY_OTHER_DETAILS)
    private WebElement ethnicityOtherDetails;

    @FindBy(xpath = YEAR_OF_BIRTH_XPATH)
    private WebElement birthYear;

    @FindBy(xpath = COUNTRY_OF_RESIDENCE_XPATH)
    private WebElement countryOfResidence;

    @FindBy(xpath = ZIP_OR_POSTAL_CODE_XPATH)
    private WebElement zipPostalCode;

    @FindBy(xpath = AGREEMENT_AND_UNDERSTANDING_OF_STORED_INFORMATION_XPATH)
    private WebElement storedInformationAgreement;

    @FindBy(xpath = SUBMIT_BUTTON_XPATH)
    private WebElement submit;

    public void waitUntilContentsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(generalContent));
        /*shortWait.until(ExpectedConditions.visibilityOf(introductionTextBox));
        shortWait.until(ExpectedConditions.visibilityOf(firstDiagnosedMonth));
        shortWait.until(ExpectedConditions.visibilityOf(firstDiagnosedYear));
        shortWait.until(ExpectedConditions.visibilityOf(locationsOfFirstDiagnosisList));
        shortWait.until(ExpectedConditions.visibilityOf(locationsOfHistoricalDiagnosisList));
        shortWait.until(ExpectedConditions.visibilityOf(locationsOfCurrentDiagnosisList));
        shortWait.until(ExpectedConditions.visibilityOf(requestOfTreatmentInformationText));
        shortWait.until(ExpectedConditions.visibilityOf(allUsedTreatments));
        shortWait.until(ExpectedConditions.visibilityOf(referralSource));
        shortWait.until(ExpectedConditions.visibilityOf(angiosarcomaExperienceOptionalInput));
        shortWait.until(ExpectedConditions.visibilityOf(ethnicityHispanicLatinxSpanish));
        shortWait.until(ExpectedConditions.visibilityOf(ethnicityEtCetera));
        shortWait.until(ExpectedConditions.visibilityOf(birthYear));
        shortWait.until(ExpectedConditions.visibilityOf(countryOfResidence));
        shortWait.until(ExpectedConditions.visibilityOf(zipPostalCode));
        shortWait.until(ExpectedConditions.visibilityOf(storedInformationAgreement));*/
        shortWait.until(ExpectedConditions.visibilityOf(submit));

        Assert.assertTrue(generalContent.isDisplayed());
    }

    public void selectMonthOfFirstDiagnosis(String month) {
        JDIPageUtils.scrollDownToElement(FIRST_DIAGNOSED_DATE_CONTENT_XPATH);
        //JDIPageUtils.selectDropdownMenuOptionUsingOptionName(month, firstDiagnosedMonth);
        Select monthDropdown = new Select(firstDiagnosedMonth);
        monthDropdown.selectByVisibleText(month);
    }

    public void selectYearOfFirstDiagnosis(String year) {
        //JDIPageUtils.selectDropdownMenuOptionUsingOptionName(year, firstDiagnosedYear);
        Select yearDropdown = new Select(firstDiagnosedYear);
        yearDropdown.selectByVisibleText(year);
    }

    /**
     * Get the checkbox using the question number and the checkbox label name
     * @param questionNumber the number of the question e.g. 1, 2, 3, 4, 5
     * @param optionName the checkbox label name e.g. Yes, No, I don't Know
     * @return
     */
    private static String getPicklistWebElementXPathByName(String questionNumber, String optionName, boolean conditionallyShown) {
        String checkboxOptionXPath = null;

        if (conditionallyShown) {
            //Find xpath based on the question number and picklist option text
            checkboxOptionXPath =  "(//li[@value=" + questionNumber + "]"
                    + "//span[normalize-space(text()) = '" + optionName + "']"
                    + "/preceding-sibling::div[@class='mat-checkbox-inner-container'])" + CONDITIONAL_SPECIFIER + "";

        } else {
            //Find xpath of condtionally shown question based on question number and picklist option text
            checkboxOptionXPath =  "//li[@value= " + questionNumber + "]"
                    + "//span[normalize-space(text()) = '" + optionName + "']"
                    + "/preceding-sibling::div[@class='mat-checkbox-inner-container']";

        }

        return checkboxOptionXPath;
    }

    public void selectLocationOfFirstDiagnosis(String location) {
        //Question 2
        logger.info("Clicking location of first diagnosis: {}", location);
        String locationXPath = getPicklistWebElementXPathByName(QUESTION_TWO, location, false);
        JDIPageUtils.scrollDownToElement(locationXPath);
        JDIPageUtils.clickUsingJavaScript(locationXPath, XPATH);
    }

    public void inputFirstDiagnosisBoneLimbDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(firstDiagnosisBoneLimbDetails));
        JDIPageUtils.inputText(firstDiagnosisBoneLimbDetails, details);
    }

    public void inputFirstDiagnosisAbdominalAreaDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(firstDiagnosisAbdominalAreaDetails));
        JDIPageUtils.inputText(firstDiagnosisAbdominalAreaDetails, details);
    }

    public void inputFirstDiagnosisOtherDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(firstDiagnosisOtherDetails));
        JDIPageUtils.inputText(firstDiagnosisOtherDetails, details);
    }

    public void selectLocationOfHistoricalDiagnosis(String location) {
        //Question 3
        logger.info("Clicking location of historical diagnosis: {}", location);
        String locationXPath = getPicklistWebElementXPathByName(QUESTION_THREE, location, false);
        JDIPageUtils.scrollDownToElement(locationXPath);
        JDIPageUtils.clickUsingJavaScript(locationXPath, XPATH);
    }

    public void inputHistoricalDiagnosisBoneLimbDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(historicalDiagnosisBoneLimbDetails));
        JDIPageUtils.inputText(historicalDiagnosisBoneLimbDetails, details);
    }

    public void inputHistoricalDiagnosisAbdominalAreaDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(historicalDiagnosisAbdominalAreaDetails));
        JDIPageUtils.inputText(historicalDiagnosisAbdominalAreaDetails, details);
    }

    public void inputHistoricalDiagnosisOtherDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(historicalDiagnosisOtherDetails));
        JDIPageUtils.inputText(historicalDiagnosisOtherDetails, details);
    }

    public void selectLocationOfCurrentDiagnosis(String location) {
        //Question 4
        logger.info("Clicking location of current diagnosis: {}", location);
        String locationXPath = getPicklistWebElementXPathByName(QUESTION_FOUR, location, false);
        JDIPageUtils.scrollDownToElement(locationXPath);
        JDIPageUtils.clickUsingJavaScript(locationXPath, XPATH);
    }

    public void inputCurrentDiagnosisBoneLimbDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(currentDiagnosisBoneLimbDetails));
        JDIPageUtils.inputText(currentDiagnosisBoneLimbDetails, details);
    }

    public void inputCurrentDiagnosisAbdominalAreaDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(currentDiagnosisAbdominalAreaDetails));
        JDIPageUtils.inputText(currentDiagnosisAbdominalAreaDetails, details);
    }

    public void inputCurrentDiagnosisOtherDetails(String details) {
        shortWait.until(ExpectedConditions.visibilityOf(currentDiagnosisOtherDetails));
        JDIPageUtils.inputText(currentDiagnosisOtherDetails, details);
    }

    public void selectHaveHadSurgeryToRemoveAngiosarcoma(String response) {
        //Question 5
        String responseXPath = getPicklistWebElementXPathByName(QUESTION_FIVE, response, false);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void selectSurgeryRemovedAllKnownCancerTissue(String response) {
        //Question 5 - conditionally shown
        String responseXPath = getPicklistWebElementXPathByName(QUESTION_FIVE, response, true);
        waitUntilConditionalQuestionShown(responseXPath);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void setHaveHadRadiationAsTreatmentForAngiosarcoma(String response) {
        //Question 6
        String responseXPath = getPicklistWebElementXPathByName(QUESTION_SIX, response, false);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void setRadiationBeforeOrAfterSugery(String response) {
        //Question 6 - conditionally shown
        String responseXPath = null;
        if (response.equalsIgnoreCase("I don\'t know")) {
            responseXPath = getPicklistWebElementXPathByName(QUESTION_SIX, response, true);

        } else {
            responseXPath = getPicklistWebElementXPathByName(QUESTION_SIX, response, false);

        }
        waitUntilConditionalQuestionShown(responseXPath);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void setAllTreatmentsPrescribed(String treatment) {
        JDIPageUtils.scrollDownToElement(ALL_USED_TREATMENTS_TEXT_INPUT_XPATH);
        JDIPageUtils.inputUsingJavaScript(treatment, allUsedTreatments);
    }

    public void setCurrentlyBeingTreatedForAngiosarcoma(String response) {
        //Question 8
        String responseXPath = getPicklistWebElementXPathByName(QUESTION_EIGHT, response, false);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void inputAllTherapiesCurrentlyRecievingForAngiosarcoma(String response) {
        shortWait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(ALL_THERAPIES_CURRENTLY_USING_FOR_ANGIOSARCOMA_XPATH)));
        JDIPageUtils.scrollDownToElement(ALL_THERAPIES_CURRENTLY_USING_FOR_ANGIOSARCOMA_XPATH);
        JDIPageUtils.inputUsingJavaScript(response, allTherapiesUsedForAngiosarcoma);
    }

    public void setHaveBeenDiagnosedWithDifferentCancer(String response) {
        //Question 9
        String responseXPath = getPicklistWebElementXPathByName(QUESTION_NINE, response, false);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void inputDiseaseNameAndYearOfDiagnosis(String diseaseName, String diagnosisYear) {
        //Question 9 - conditionally shown
        shortWait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(OTHER_CANCER_DISEASE_NAME_XPATH)));
        JDIPageUtils.scrollDownToElement(OTHER_CANCER_DISEASE_NAME_XPATH);
        JDIPageUtils.inputUsingJavaScript(diseaseName, otherCancerDiseaseName);
        JDIPageUtils.inputUsingJavaScript(diagnosisYear, otherCancerDiagnosisYear);
    }

    public void setHadRadiationAsTreatmentForAnotherCancer(String response) {
        //Question 10
        String responseXPath = getPicklistWebElementXPathByName(QUESTION_TEN, response, false);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void inputLocationOfRadioationForOtherCancer(String response) {
        shortWait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(OTHER_CANCER_LOCATION_OF_RADIATION_TREATMENT_XPATH)));
        JDIPageUtils.scrollDownToElement(OTHER_CANCER_LOCATION_OF_RADIATION_TREATMENT_XPATH);
        JDIPageUtils.inputUsingJavaScript(response, locationOfRadiationTreatmentForOtherCancer);
    }

    public void setReferralSource(String source) {
        JDIPageUtils.scrollDownToElement(ANGIOSARCOMA_HOW_HEARD_ABOUT_PROJECT_XPATH);
        JDIPageUtils.inputUsingJavaScript(source, referralSource);
    }

    public void setExperienceWithAngiosarcomaOptionalInput(String input) {
        JDIPageUtils.scrollDownToElement(ANGIOSARCOMA_EXPERIENCE_OPTIONAL_TEXT_INPUT_XPATH);
        JDIPageUtils.inputUsingJavaScript(input, angiosarcomaExperienceOptionalInput);
    }

    public void setUserEthnicityAsHispanicLatinxOrSpanish(String response) {
        //Question 13
        logger.info("User is Hispanic/Latinx/Spanish: {}", response);
        String responseXPath = getPicklistWebElementXPathByName(QUESTION_THIRTEEN, response, false);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void setUserEthnicityAsEtCetera(String ethnicity) {
        //Question 14
        logger.info("User is: {}", ethnicity);
        String responseXPath = getPicklistWebElementXPathByName(QUESTION_FOURTEEN, ethnicity, false);
        JDIPageUtils.scrollDownToElement(responseXPath);
        JDIPageUtils.clickUsingJavaScript(responseXPath, XPATH);
    }

    public void setEthnicityOtherDetails(String details) {
        shortWait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(ETHNICITY_OTHER_DETAILS)));
        JDIPageUtils.inputUsingJavaScript(details, ethnicityOtherDetails);
    }

    public void setBirthYear(String year) {
        JDIPageUtils.scrollDownToElement(YEAR_OF_BIRTH_XPATH);
        //JDIPageUtils.selectDropdownMenuOptionUsingOptionName(year, birthYear);
        Select yearDropdown = new Select(birthYear);
        yearDropdown.selectByVisibleText(year);
    }

    public void setCountryOfResidence(String country) {
        JDIPageUtils.scrollDownToElement(COUNTRY_OF_RESIDENCE_XPATH);
        //JDIPageUtils.selectDropdownMenuOptionUsingOptionName(country, countryOfResidence);
        Select countryOfResidenceDropdown = new Select(countryOfResidence);
        countryOfResidenceDropdown.selectByVisibleText(country);
    }

    public void setZipcode(String zipcode) {
        JDIPageUtils.scrollDownToElement(ZIP_OR_POSTAL_CODE_XPATH);
        JDIPageUtils.inputUsingJavaScript(zipcode, zipPostalCode);
    }

    public void clickSubmit() {
        JDIPageUtils.scrollDownToElement(SUBMIT_BUTTON_XPATH);
        JDIPageUtils.clickButtonUsingJDI(SUBMIT_BUTTON_XPATH, XPATH);
    }

    private void waitUntilConditionalQuestionShown(String xpath) {
        WebElement conditionalElement = getDriver().findElement(By.xpath(xpath));
        shortWait.until(ExpectedConditions.visibilityOf(conditionalElement));
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
        waitUntilContentsDisplayed();
    }
}
