package org.broadinstitute.ddp.pages.basilpages;

import java.util.List;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByTag;
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

    private static final String CONSENT_FORM_WEBELEMENTS = "ddp-loading";
    private static final String AT_LEAST_TWENTY_ONE_STABLE_ID = "*[data-ddp-test*=AT_LEAST_21]";
    private static final String AT_LEAST_TWENTY_ONE_DESCRIPTION = "at least 21";
    private static final String AGREE_TO_PARTICIPATE_IN_STUDY_STABLE_ID = "*[data-ddp-test*=AGREE_TO_PARTICIPATE]";
    private static final String AGREE_TO_PARTICIPATE_IN_STUDY_DESCRIPTION = "agree to participate in basil study";
    private static final String AGREE_TO_SHARE_MEDICAL_RECORDS_STABLE_ID = "*[data-ddp-test*=SHARE_MEDICAL_RECORDS]";
    private static final String AGREE_TO_SHARE_MEDICAL_RECORDS_DESCRIPTION = "agree to share medical records";
    private static final String AGREE_TO_SHARE_GENETIC_INFORMATION_STABLE_ID = "*[data-ddp-test*=SHARE_DNA]";
    private static final String AGREE_TO_SHARE_GENETIC_INFORMATION_DESCRIPTION = "agree to share dna";
    private static final String ANSWER_TRUE_XPATH = "//*[text()='Yes']";
    private static final String ANSWER_TRUE_STATUS = "Yes";
    private static final String ANSWER_FALSE_XPATH = "//*[text()='No']";
    private static final String ANSWER_FALSE_STATUS = "No";
    private static final String ANSWER_BLANK_STATUS = "Blank";
    private static final String USER_SIGNATURE_STABLE_ID = "*[data-ddp-test*=SIGNATURE]";
    private static final String USER_DATE_OF_BIRTH_MONTH = "//input[contains(@placeholder, 'MM')]";
    private static final String USER_DATE_OF_BIRTH_DAY = "//input[contains(@placeholder, 'DD')]";
    private static final String USER_DATE_OF_BIRTH_YEAR = "//input[contains(@placeholder, 'YYYY')]";
    private static final String SUBMIT_BUTTON_XPATH = "//button[normalize-space(text()) = 'SUBMIT']";
    private static final String VALIDATION_ERROR_TAG = "ddp-validation-message";
    private static final String RETRIEVE_MONTH = "month";
    private static final String RETRIEVE_DAY = "day";
    private static final String RETRIEVE_YEAR = "year";

    @FindBy(css = AT_LEAST_TWENTY_ONE_STABLE_ID)
    private WebElement atLeastTwentyone;

    @FindBy(css = AGREE_TO_PARTICIPATE_IN_STUDY_STABLE_ID)
    private WebElement agreeToParticipateInBasilStudy;

    @FindBy(css = AGREE_TO_SHARE_MEDICAL_RECORDS_STABLE_ID)
    private WebElement agreeToShareMedicalRecords;

    @FindBy(css = AGREE_TO_SHARE_GENETIC_INFORMATION_STABLE_ID)
    private WebElement agreeToShareDna;

    @FindBy(css = USER_SIGNATURE_STABLE_ID)
    private WebElement userSignature;

    @FindBy(xpath = USER_DATE_OF_BIRTH_MONTH)
    private WebElement birthMonth;

    @FindBy(xpath = USER_DATE_OF_BIRTH_DAY)
    private WebElement birthDay;

    @FindBy(xpath = USER_DATE_OF_BIRTH_YEAR)
    private WebElement birthYear;

    @FindBy(xpath = SUBMIT_BUTTON_XPATH)
    private Button submit;

    @ByTag(CONSENT_FORM_WEBELEMENTS)
    private WebElement consentFormElements;

    @ByTag(VALIDATION_ERROR_TAG)
    private List<WebElement> validationErrors;


    public void setAtLeastTwentyone(boolean isTwentyOne) {
        JDIPageUtils.selectRadioButtonOption(atLeastTwentyone, isTwentyOne);
    }

    public void setAgreeToParticipateInBasilStudy(boolean agreeToParticipate) {
        JDIPageUtils.selectRadioButtonOption(agreeToParticipateInBasilStudy, agreeToParticipate);
    }

    public void setAgreeToShareMedicalRecords(boolean agreeToShareRecords) {
        JDIPageUtils.selectRadioButtonOption(agreeToShareMedicalRecords, agreeToShareRecords);
    }

    public void setAgreeToShareGeneticInformation(boolean agreeToShareGeneticInformation) {
        JDIPageUtils.selectRadioButtonOption(agreeToShareDna, agreeToShareGeneticInformation);
    }

    public void setSignature(String signature) {
        JDIPageUtils.inputText(userSignature, signature);
    }

    public void setBirthdayInformation(String birthdate) {
        String month = JDIPageUtils.parseBirthdateInformation(birthdate, RETRIEVE_MONTH);
        String day = JDIPageUtils.parseBirthdateInformation(birthdate, RETRIEVE_DAY);
        String year = JDIPageUtils.parseBirthdateInformation(birthdate, RETRIEVE_YEAR);

        setBirthMonth(month);
        setBirthDate(day);
        setBirthYear(year);
    }

    public void setBirthMonth(String month) {
        JDIPageUtils.inputText(birthMonth, month);
    }

    public void setBirthDate(String day) {
        JDIPageUtils.inputText(birthDay, day);
    }

    public void setBirthYear(String year) {
        JDIPageUtils.inputText(birthYear, year);
    }

    public void clickSubmit() {
        JDIPageUtils.scrollDownToElement(SUBMIT_BUTTON_XPATH);
        JDIPageUtils.clickButtonUsingJDI(SUBMIT_BUTTON_XPATH, XPATH);
    }

    public void verifyConsentFormDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(atLeastTwentyone));
        shortWait.until(ExpectedConditions.visibilityOf(agreeToParticipateInBasilStudy));
        shortWait.until(ExpectedConditions.visibilityOf(agreeToShareMedicalRecords));
        shortWait.until(ExpectedConditions.visibilityOf(agreeToShareDna));
        shortWait.until(ExpectedConditions.visibilityOf(userSignature));
        shortWait.until(ExpectedConditions.visibilityOfAllElements(birthMonth, birthDay, birthYear));
        Assert.assertTrue(consentFormElements.isDisplayed());
    }

    private WebElement getBooleanWebElementFromDescription(String questionDrescription) {
        WebElement element = null;

        if (questionDrescription.equalsIgnoreCase(AT_LEAST_TWENTY_ONE_DESCRIPTION)) {
            element = atLeastTwentyone;

        } else if (questionDrescription.equalsIgnoreCase(AGREE_TO_PARTICIPATE_IN_STUDY_DESCRIPTION)) {
            element = agreeToParticipateInBasilStudy;

        } else if (questionDrescription.equalsIgnoreCase(AGREE_TO_SHARE_MEDICAL_RECORDS_DESCRIPTION)) {
            element = agreeToShareMedicalRecords;

        } else if (questionDrescription.equalsIgnoreCase(AGREE_TO_SHARE_GENETIC_INFORMATION_DESCRIPTION)) {
            element = agreeToShareDna;

        }
        return element;
    }

    public void verifyBooleanQuestionStatus(String questionDescription, String questionStatus) {
        WebElement element = getBooleanWebElementFromDescription(questionDescription);

        if (questionStatus.equalsIgnoreCase(ANSWER_TRUE_STATUS)) {
            JDIPageUtils.verifyRadioButtonAnswerIsSelected(element, ANSWER_TRUE_XPATH);

        } else if (questionStatus.equalsIgnoreCase(ANSWER_FALSE_STATUS)) {
            JDIPageUtils.verifyRadioButtonAnswerIsSelected(element, ANSWER_FALSE_XPATH);

        } else if (questionStatus.equalsIgnoreCase(ANSWER_BLANK_STATUS)) {
            JDIPageUtils.verifyRadioButtonUnanswered(element, ANSWER_TRUE_XPATH, ANSWER_FALSE_XPATH);

        }
    }

    public void verifySignatureIsEmpty() {
        JDIPageUtils.verifyTextFieldIsEmpty(userSignature);
    }

    public void verifyDateOfBirthIsEmpty() {
        JDIPageUtils.verifyTextFieldIsEmpty(birthMonth);
        JDIPageUtils.verifyTextFieldIsEmpty(birthDay);
        JDIPageUtils.verifyTextFieldIsEmpty(birthYear);
    }

    public void verifyValidationErrorsAreDisplayed() {
        for (WebElement error : validationErrors) {
            shortWait.until(ExpectedConditions.visibilityOf(error));
            Assert.assertTrue(error.isDisplayed());
        }
        logger.info("Amount of validation errors: {}", validationErrors.size());
    }

    /**
     * Waits until url is reached (and previous url is finished) before checking.
     * urlTemplate is set at BasilAppSite.java for each page object
     */
    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        verifyConsentFormDisplayed();
    }
}
