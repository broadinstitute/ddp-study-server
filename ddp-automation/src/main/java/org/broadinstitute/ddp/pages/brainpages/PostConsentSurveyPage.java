package org.broadinstitute.ddp.pages.brainpages;

import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostConsentSurveyPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(PostConsentSurveyPage.class);


    private static final String BRAIN_CANCER_RESPONSE_HAVE_NOT_RECEIVED_ANY_MEDICATIONS = "I have not received any medications"
            + " for treatment of my brain cancer";

    private static final String BRAIN_CANCER_RESPONSE_DO_NOT_KNOW_THE_NAMES_OF_THE_MEDICATIONS = "I do not know"
            + " the names of the medications";


    private static final String BRAIN_CANCER_MEDICATION_WAS_PART_OF_A_CLINICAL_TRIAL = "This was part of a clinical trial";

    private static final String BRAIN_CANCER_ADD_ANOTHER_MEDICATION_THERAPY_BUTTON_TEXT = "+ Add another medication/therapy";
    private static final String BRAIN_CANCER_ADD_ANOTHER_CANCER_DIAGNOSIS_BUTTON_TEXT = "+ Add another cancer diagnosis";

    private static final String RESPONSE_UNSURE_HOW_TO_ANSWER = "Unsure how to answer";
    private static final String RESPONSE_OTHER = "Other";
    private static final String RESPONSE_YES = "Yes";
    private static final String RESPONSE_NO = "No";
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
    private static final String STABLE_ID_THERAPY_NAME = "THERAPY_NAME";
    private static final String STABLE_ID_OTHER_CANCER_TYPE = "OTHER_CANCER_TYPE";
    private static final String STABLE_ID_EXPERIENCE = "EXPERIENCE";
    private static final String PLACEHOLDER_FOR_YEAR_OF_DIAGNOSIS = "Year of diagnosis";
    private static final String SUBMIT_BUTTON_XPATH = "//button[normalize-space(text()) = 'SUBMIT']";

    @FindBy(xpath = SUBMIT_BUTTON_XPATH)
    private WebElement submitBUtton;


    // 1. Have you ever had any of the following surgical procedures for your brain cancer?
    public void setSurgicalProcedure(String procedure) {
        String questionXPath = JDIPageUtils.createXPathForGeneralCheckboxWebElement(QUESTION_ONE, procedure, null);
        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
        logger.info("User selected: {}", procedure);
    }

    public void inputSurgicalProcedureUnsureHowToAnswerDetails(String details) {
        String questionXPath = JDIPageUtils.createXPathForGeneralConditionalTextfieldWebElement(QUESTION_ONE,
                RESPONSE_UNSURE_HOW_TO_ANSWER, null);

        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        WebElement textfield = getWebElementUsingXPath(questionXPath);
        JDIPageUtils.inputText(textfield, details);
    }

    public void inputSurgicalProcedureOtherDetails(String details) {
        String questionXPath = JDIPageUtils.createXPathForGeneralConditionalTextfieldWebElement(QUESTION_ONE,
                RESPONSE_OTHER, null);

        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        WebElement textfield = getWebElementUsingXPath(questionXPath);
        JDIPageUtils.inputText(textfield, details);
    }

    // 2. Have you ever had radiation for treatment of your brain cancer?
    public void setHaveHadRadiationTreatmentforBrainCancer(String response) {
        String questionXPath = JDIPageUtils.createXPathForGeneralRadioButtonWebElement(QUESTION_TWO, response, null);
        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        WebElement radioButton = getWebElementUsingXPath(questionXPath);
        JDIPageUtils.clickUsingJavaScript(radioButton);
    }

    // 2a. What kind of radiation therapy or therapies have you received for treatment of your brain cancer?
    public void setRadiationTherapyReceived(String therapy) {
        String questionXPath = JDIPageUtils.createXPathForGeneralCheckboxWebElement(QUESTION_TWO, therapy, null);
        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
        logger.info("User selected: {}", therapy);
    }

    public void inputRadiationTherapyReceivedOtherDetails(String details) {
        String questionXPath = JDIPageUtils.createXPathForGeneralConditionalTextfieldWebElement(QUESTION_TWO, RESPONSE_OTHER, null);
        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        WebElement textfield = getWebElementUsingXPath(questionXPath);
        JDIPageUtils.inputText(textfield, details);
    }

    public void inputRadiationTherapyReceivedUnsureHowToAnswerDetails(String details) {
        String questionXPath = JDIPageUtils.createXPathForGeneralConditionalTextfieldWebElement(QUESTION_TWO,
                RESPONSE_UNSURE_HOW_TO_ANSWER, null);

        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        WebElement textfield = getWebElementUsingXPath(questionXPath);
        JDIPageUtils.inputText(textfield, details);
    }

    // 3. Have you received any medications/chemotherapies for treatment of your brain cancer?
    public void selectHaveNotReceivedMedicationForTreatment() {
        String questionXPath = JDIPageUtils.createXPathForGeneralCheckboxWebElement(QUESTION_THREE,
                BRAIN_CANCER_RESPONSE_HAVE_NOT_RECEIVED_ANY_MEDICATIONS,
                null);
        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
        logger.info("User selected: {}", BRAIN_CANCER_RESPONSE_HAVE_NOT_RECEIVED_ANY_MEDICATIONS);
    }

    public void selectDoNotKnowTheNamesOfTheMedications() {
        String questionXPath = JDIPageUtils.createXPathForGeneralCheckboxWebElement(QUESTION_THREE,
                BRAIN_CANCER_RESPONSE_DO_NOT_KNOW_THE_NAMES_OF_THE_MEDICATIONS,
                null);
        shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(questionXPath)));
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
        logger.info("User selected: {}", BRAIN_CANCER_RESPONSE_DO_NOT_KNOW_THE_NAMES_OF_THE_MEDICATIONS);
    }

    public void inputTherapyPreviouslyOrCurrentlyUsed(String therapy) {
        String questionXPath = JDIPageUtils.createXPathForInputWebElementUsingStableId(QUESTION_THREE, STABLE_ID_THERAPY_NAME, null);
        WebElement textfield = getWebElementUsingXPath(questionXPath);
        shortWait.until(ExpectedConditions.visibilityOf(textfield));
        JDIPageUtils.inputText(textfield, therapy);
    }

    public void clickTherapyWasPartOfAClinicalTrial() {
        String questionXPath = JDIPageUtils.createXPathForGeneralCheckboxWebElement(QUESTION_THREE,
                BRAIN_CANCER_MEDICATION_WAS_PART_OF_A_CLINICAL_TRIAL,
                null);

        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
    }

    public void clickAddAnotherMedicationTherapy() {
        String questionXPath = JDIPageUtils.createXPathForButtonWebElementsUsingButtonText(QUESTION_THREE,
                BRAIN_CANCER_ADD_ANOTHER_MEDICATION_THERAPY_BUTTON_TEXT,
                null);

        WebElement button = getWebElementUsingXPath(questionXPath);
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(button);
    }

    // 4. Have you ever been diagnosed with any other cancers?
    public void setHaveBeenDiagnosedWithOtherCancers(String response) {
        String questionXPath = JDIPageUtils.createXPathForGeneralRadioButtonWebElement(QUESTION_FOUR,
                response,
                null);

        WebElement radiobutton = getWebElementUsingXPath(questionXPath);
        shortWait.until(ExpectedConditions.visibilityOf(radiobutton));
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(radiobutton);
    }

    public void inputNameOfOtherCancer(String cancerName) {
        String questionXPath = JDIPageUtils.createXPathForInputWebElementUsingStableId(QUESTION_FOUR, STABLE_ID_OTHER_CANCER_TYPE, null);
        WebElement textfield = getWebElementUsingXPath(questionXPath);
        shortWait.until(ExpectedConditions.visibilityOf(textfield));
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.inputText(textfield, cancerName);
        textfield.sendKeys(Keys.ESCAPE);
    }

    public void inputYearOfDiagnosisOfOtherCancer(String year) {
        String questionXPath = JDIPageUtils.createXPathforInputWebElementUsingPlaceholder(QUESTION_FOUR,
                PLACEHOLDER_FOR_YEAR_OF_DIAGNOSIS,
                null);

        WebElement textfield = getWebElementUsingXPath(questionXPath);
        shortWait.until(ExpectedConditions.visibilityOf(textfield));
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.inputText(textfield, year);
    }

    // 5. What is your gender? Select all that apply
    public void setGender(String gender) {
        String questionXPath = JDIPageUtils.createXPathForGeneralCheckboxWebElement(QUESTION_FIVE, gender, null);
        //WebElement checkbox = getWebElementUsingXPath(questionXPath);
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
    }

    // 6. Are you transgender? (That is, is your current gender different than what it is/was listed
    //    in your birth certificate?)
    public void setIsTransgender(String response) {
        String questionXPath = JDIPageUtils.createXPathForGeneralRadioButtonWebElement(QUESTION_SIX, response, null);
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
    }

    // 7. Do you consider yourself Hispanic, Latino/a or Spanish?
    public void setIsHispanicLatinaOrSpanish(String response) {
        String questionXPath = JDIPageUtils.createXPathForGeneralRadioButtonWebElement(QUESTION_SEVEN, response, null);
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
    }

    // 8. What is your race? Select all that apply.
    public void setRace(String race) {
        String questionXPath = JDIPageUtils.createXPathForGeneralCheckboxWebElement(QUESTION_EIGHT, race, null);
        //WebElement checkbox = getWebElementUsingXPath(questionXPath);
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.clickUsingJavaScript(questionXPath, XPATH);
    }


    // 9. Tell us anything else you would like to about yourself or your brain cancer.
    public void inputExperienceWithBrainCancer(String experience) {
        String questionXPath = JDIPageUtils.createXPathForTextareaWebElementsUsingStableId(QUESTION_NINE,
                STABLE_ID_EXPERIENCE,
                null);

        WebElement textfield = getWebElementUsingXPath(questionXPath);
        shortWait.until(ExpectedConditions.visibilityOf(textfield));
        JDIPageUtils.scrollDownToElement(questionXPath);
        JDIPageUtils.inputText(textfield, experience);
    }

    public void clickSubmit() {
        shortWait.until(ExpectedConditions.visibilityOf(submitBUtton));
        JDIPageUtils.scrollDownToElement(SUBMIT_BUTTON_XPATH);
        JDIPageUtils.clickUsingJavaScript(submitBUtton);
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
    }
}
