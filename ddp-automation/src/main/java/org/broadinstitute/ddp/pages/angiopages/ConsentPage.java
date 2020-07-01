package org.broadinstitute.ddp.pages.angiopages;

import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsentPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(ConsentPage.class);

    //Consent subtitle
    private static final String ACTIVITY_SUBTITLE_XPATH = "//div[contains(@class, 'PageHeader-activity-subtitle')]";

    //Contained in all 3 page steppers
    private static final String CONSENT_INSTRUCTIONS_XPATH = "(//div//ddp-activity-section)[1]";
    //Contained in 1. Key Points & 2. Full Form
    private static final String CONSENT_TEXT_CONTENT_XPATH = "(//div//ddp-activity-section)[2]";
    private static final String CONSENT_LAST_UPDATED_TEXT_XPATH = "//div[contains(@class, 'LastUpdatedText')]";
    private static final String CONSENT_NEXT_BUTTON_XPATH = "//div[contains(@class, 'NextButton')]"
            + "//button[normalize-space(text()) = 'NEXT']";

    private static final String PREVIOUS_BUTTON_XPATH = "//button[contains(@class, 'mat-raised-button')]"
            + "[normalize-space(text()) = 'PREV']";

    private static final String KEY_POINTS_PAGE_STEPPER_XPATH = "//div[contains(@class, 'WizardSteps-title')]"
            + "[normalize-space(text()) = '1. Key Points']";
    private static final String FULL_FORM_PAGE_STEPPER_XPATH = "//div[contains(@class, 'WizardSteps-title')]"
            + "[normalize-space(text()) = '2. Full Form']";
    private static final String SIGN_CONSENT_PAGE_STEPPER_XPATH = "//div[contains(@class, 'WizardSteps-title')]"
            + "[normalize-space(text()) = '3. Sign Consent']";

    private static final String AGREE_TO_SAMPLE_OF_BLOOD_BEING_DRAWN_YES_XPATH = "//li[contains(@class, 'ddp-question')][1]"
            + "//*[normalize-space(text()) = 'Yes']";
    private static final String AGREE_TO_SAMPLE_OF_BLOOD_BEING_DRAWN_NO_XPATH = "//li[contains(@class, 'ddp-question')][1]"
            + "//*[normalize-space(text()) = 'No']";
    private static final String AGREE_TO_STORED_TISSUE_SAMPLE_YES_XPATH = "//li[contains(@class, 'ddp-question')][2]"
            + "//*[normalize-space(text()) = 'Yes']";
    private static final String AGREE_TO_STORED_TISSUE_SAMPLE_NO_XPATH = "//li[contains(@class, 'ddp-question')][2]"
            + "//*[normalize-space(text()) = 'No']";

    private static final String FULL_NAME_SIGNATURE_XPATH = "//*[contains(@data-ddp-test, 'CONSENT_FULLNAME')]";

    private static final String DATE_OF_BIRTH_MONTH_XPATH = "//*[contains(@placeholder, 'MM')]";
    private static final String DATE_OF_BIRTH_DAY_XPATH = "//*[contains(@placeholder, 'DD')]";
    private static final String DATE_OF_BIRTH_YEAR_XPATH = "//*[contains(@placeholder, 'YYYY')]";

    private static final String SIGN_CONSENT_SUBMIT_BUTTON_XPATH = "//button[contains(@class, 'mat-raised-button')]"
            + "[normalize-space(text()) = 'SUBMIT']";
    private static final String SUBMIT_BUTTON_TEXT = "SUBMIT";

    @FindBy(xpath = ACTIVITY_SUBTITLE_XPATH)
    private WebElement activitySubtitle;

    @FindBy(xpath = CONSENT_INSTRUCTIONS_XPATH)
    private WebElement consentInstructions;

    @FindBy(xpath = CONSENT_TEXT_CONTENT_XPATH)
    private WebElement consentProjectExplanationText;

    @FindBy(xpath = CONSENT_LAST_UPDATED_TEXT_XPATH)
    private WebElement consentDocumentLastUpdatedDate;

    @FindBy(xpath = CONSENT_NEXT_BUTTON_XPATH)
    private WebElement nextButton;

    @FindBy(xpath = PREVIOUS_BUTTON_XPATH)
    private WebElement previousButton;

    @FindBy(xpath = KEY_POINTS_PAGE_STEPPER_XPATH)
    private WebElement stepperKeyPoints;

    @FindBy(xpath = FULL_FORM_PAGE_STEPPER_XPATH)
    private WebElement stepperFullForm;

    @FindBy(xpath = SIGN_CONSENT_PAGE_STEPPER_XPATH)
    private WebElement stepperSignConsent;

    @FindBy(xpath = AGREE_TO_SAMPLE_OF_BLOOD_BEING_DRAWN_YES_XPATH)
    private WebElement drawSampleOfBloodYes;

    @FindBy(xpath = AGREE_TO_SAMPLE_OF_BLOOD_BEING_DRAWN_NO_XPATH)
    private WebElement drawSampleOfBloodNo;

    @FindBy(xpath = AGREE_TO_STORED_TISSUE_SAMPLE_YES_XPATH)
    private WebElement storeTissueSampleYes;

    @FindBy(xpath = AGREE_TO_STORED_TISSUE_SAMPLE_NO_XPATH)
    private WebElement storeTissueSampleNo;

    @FindBy(xpath = FULL_NAME_SIGNATURE_XPATH)
    private WebElement signature;

    @FindBy(xpath = DATE_OF_BIRTH_MONTH_XPATH)
    private WebElement birthMonth;

    @FindBy(xpath = DATE_OF_BIRTH_DAY_XPATH)
    private WebElement birthDay;

    @FindBy(xpath = DATE_OF_BIRTH_YEAR_XPATH)
    private WebElement birthYear;

    @FindBy(xpath = SIGN_CONSENT_SUBMIT_BUTTON_XPATH)
    private WebElement submitButton;

    public void verifyGeneralConsentContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(consentInstructions));
        JDIPageUtils.scrollDownToElement(CONSENT_INSTRUCTIONS_XPATH);
        Assert.assertTrue(consentInstructions.isDisplayed());
        logger.info("Consent intructions are displayed");
    }

    public void setAgreeToSampleOfBloodBeingDrawn(String response) {
        if (response.equalsIgnoreCase(YES)) {
            shortWait.until(ExpectedConditions.visibilityOf(drawSampleOfBloodYes));
            JDIPageUtils.scrollDownToElement(AGREE_TO_SAMPLE_OF_BLOOD_BEING_DRAWN_YES_XPATH);
            drawSampleOfBloodYes.click();

        } else if (response.equalsIgnoreCase(NO)) {
            shortWait.until(ExpectedConditions.visibilityOf(drawSampleOfBloodNo));
            JDIPageUtils.scrollDownToElement(AGREE_TO_SAMPLE_OF_BLOOD_BEING_DRAWN_NO_XPATH);
            drawSampleOfBloodNo.click();

        }
    }

    public void setAgreeToStoredTissueSample(String response) {
        if (response.equalsIgnoreCase(YES)) {
            JDIPageUtils.scrollDownToElement(AGREE_TO_STORED_TISSUE_SAMPLE_YES_XPATH);
            storeTissueSampleYes.click();

        } else if (response.equalsIgnoreCase(NO)) {
            JDIPageUtils.scrollDownToElement(AGREE_TO_STORED_TISSUE_SAMPLE_NO_XPATH);
            storeTissueSampleNo.click();

        }
    }

    public void inputSignature(String fullName) {
        JDIPageUtils.scrollDownToElement(FULL_NAME_SIGNATURE_XPATH);
        JDIPageUtils.inputText(signature, fullName);
    }

    public void setDateOfBirth(String dateOfBirth) {
        String month = JDIPageUtils.parseBirthdateInformation(dateOfBirth, MONTH);
        String day = JDIPageUtils.parseBirthdateInformation(dateOfBirth, DAY);
        String year = JDIPageUtils.parseBirthdateInformation(dateOfBirth, YEAR);

        setBirthMonth(month);
        setBirthDay(day);
        setBirthYear(year);
    }

    private void setBirthMonth(String month) {
        JDIPageUtils.scrollDownToElement(DATE_OF_BIRTH_MONTH_XPATH);
        JDIPageUtils.inputText(birthMonth, month);
    }

    private void setBirthDay(String day) {
        JDIPageUtils.inputText(birthDay, day);
    }

    private void setBirthYear(String year) {
        JDIPageUtils.inputText(birthYear, year);
    }

    public void verifyDateConsentLastUpdatedDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(consentDocumentLastUpdatedDate));
        JDIPageUtils.scrollDownToElement(CONSENT_LAST_UPDATED_TEXT_XPATH);
        Assert.assertTrue(consentDocumentLastUpdatedDate.isDisplayed());
    }

    public void clickNext() {
        shortWait.until(ExpectedConditions.visibilityOf(nextButton));
        JDIPageUtils.scrollDownToElement(CONSENT_NEXT_BUTTON_XPATH);
        nextButton.click();
    }

    public void clickSubmit() {
        shortWait.until(ExpectedConditions.visibilityOf(submitButton));
        JDIPageUtils.scrollDownToElement(SIGN_CONSENT_SUBMIT_BUTTON_XPATH);
        //Double click since there is not a reliable method to first give the button
        //a focused or hovered over state. First click gives the button focus.
        //todo tech debt - find better solution for fcousing/hovering over webelements
        if (submitButton.isDisplayed()) {
            JDIPageUtils.doubleClickAndWait(submitButton);
        }
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
    }
}
