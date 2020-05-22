package org.broadinstitute.ddp.pages.brainpages;

import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
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
    private static final String GENERAL_PAGE_CONTENT = "//article[contains(@class, 'PageContent')]";

    //Introduction text box
    private static final String INTRODUCTION_TEXT_BOX_XPATH = "//div[@class='PageContent-box']";

    // 1. First diagnosed with brain cancer date (in month + year format)
    private static final String FIRST_DIAGNOSED_DATE_CONTENT_XPATH = "//li[@value=1]/parent::ol";
    private static final String FIRST_DIAGNOSED_DATE_MONTH_XPATH = "//option[contains(text(), 'Choose month...')]//parent::select";
    private static final String FIRST_DIAGNOSED_DATE_YEAR_XPATH = "(//option[contains(text(), 'Choose year...')]//parent::select)[1]";

    // 2. When you were first diagnosed with brain cancer, what type was it?
    private static final String BRAIN_CANCER_INITIAL_DIAGNOSIS_TEXTFIELD_XPATH = "//li[@value=2]"
            + "//input[contains(@data-ddp-test, 'CANCER_TYPE')]";

    // 3. Grade of diagnosis of different type of brain cancer
    private static final String GRADE_DIAGNOSIS_OF_DIFFERENT_TYPE_OF_BRAIN_CANCER = "//li[@value = 3]"
            + "//option[contains(text(), 'Choose grade')]//parent::select";

    // 4. Since your first diagnosis, has the type of your brain cancer changed?
    private static final String BRAIN_CANCER_CHANGED_YES_XPATH = "//li[@value=4]"
            + "//mat-radio-button//div[contains(text(), 'Yes')]//preceding-sibling::div";
    private static final String BRAIN_CANCER_CHANGED_NO_XPATH = "//li[@value=4]"
            + "//mat-radio-button//div[contains(text(), 'No')]//preceding-sibling::div";
    private static final String BRAIN_CANCER_CHANGED_UNSURE_HOW_TO_ANSWER_XPATH = "//li[@value=4]"
            + "//mat-radio-button//div[contains(text(), 'Unsure how to answer')]//preceding-sibling::div";

    // 4a. What type did your brain cancer change to and when?
    private static final String BRAIN_CANCER_TYPE_XPATH = "//input[contains(@data-ddp-test, 'OTHER_BRAIN_CANCER_TYPE')]";
    private static final String BRAIN_CANCER_TYPE_YEAR_OF_DIAGNOSIS_XPATH = "(//li[@value=4]//ddp-date//select)[1]";
    private static final String BRAIN_CANCER_TYPE_MONTH_OF_DIAGNOSIS_XPATH = "(//li[@value=4]//ddp-date//select)[2]";
    private static final String ADD_ANOTHER_CHANGE_IN_TYPE_BUTTON_XPATH = "//li[@value=4]"
            + "//button[contains(text(), 'Add Another Change in Type')]";

    // 5. Since your first diagnosis, has the grade of your brain cancer changed?
    private static final String BRAIN_CANCER_GRADE_CHANGED_YES_XPATH = "//li[@value=5]"
            + "//mat-radio-button//div[contains(text(), 'Yes')]//preceding-sibling::div";
    private static final String BRAIN_CANCER_GRADE_CHANGED_NO_XPATH = "//li[@value=5]"
            + "//mat-radio-button//div[contains(text(), 'No')]//preceding-sibling::div";
    private static final String BRAIN_CANCER_GRADE_CHANGED_UNSURE_HOW_TO_ANSWER_XPATH = "//li[@value=5]"
            + "//mat-radio-button//div[contains(text(), 'Unsure how to answer')]//preceding-sibling::div";

    // 5a. What grade did your brain cancer change to and when?
    private static final String BRAIN_CANCER_GRADE_XPATH = "(//li[@value=5]//select)[1]";
    private static final String BRAIN_CANCER_YEAR_OF_GRADE_CHANGE_XPATH = "(//li[@value=5]//ddp-date//select)[1]";
    private static final String BRAIN_CANCER_MONTH_OF_GRADE_CHANGE_XPATH = "(//li[@value=5]//ddp-date//select)[2]";
    private static final String ADD_ANOTHER_CHANGE_IN_GRADE_BUTTON_XPATH = "//li[@value=5]"
            + "//button[contains(text(), 'Add Another Change in Grade')]";

    // 6. In what year were you born?
    private static final String YEAR_OF_BIRTH_XPATH = "//li[@value = 6]"
            + "//option[contains(text(), 'Choose year...')]//parent::select";

    // 7. What country do you live in?
    private static final String COUNTRY_OF_RESIDENCE_XPATH = "//li[@value = 7]"
            + "//option[contains(text(), 'Choose country...')]//parent::select";

    // 8. What is you ZIP or postal code?
    private static final String RESIDENTIAL_ZIPCODE_XPATH = "//li[@value=8]//input[contains(@data-ddp-test, 'POSTAL_CODE')]";

    // 9. How did you hear about the project?
    private static final String REFERRAL_SOURCE_XPATH = "//textarea[contains(@data-ddp-test, 'REFERRAL_SOURCE')]";

    //Submit button
    private static final String SUBMIT_BUTTON_XPATH = "//button[contains(text(), 'SUBMIT')]";


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

    @FindBy(xpath = BRAIN_CANCER_INITIAL_DIAGNOSIS_TEXTFIELD_XPATH)
    private WebElement brainCancerInitialDiagnosis;

    @FindBy(xpath = GRADE_DIAGNOSIS_OF_DIFFERENT_TYPE_OF_BRAIN_CANCER)
    private WebElement gradeAtDiagnosis;

    @FindBy(xpath = BRAIN_CANCER_CHANGED_YES_XPATH)
    private WebElement brainCancerChangedYes;

    @FindBy(xpath = BRAIN_CANCER_CHANGED_NO_XPATH)
    private WebElement brainCancerChangedNo;

    @FindBy(xpath = BRAIN_CANCER_CHANGED_UNSURE_HOW_TO_ANSWER_XPATH)
    private WebElement brainCancerChangedUnsure;

    @FindBy(xpath = BRAIN_CANCER_TYPE_XPATH)
    private WebElement brainOtherCancerType;

    @FindBy(xpath = BRAIN_CANCER_TYPE_YEAR_OF_DIAGNOSIS_XPATH)
    private WebElement brainOtherCancerYearOfDiagnosis;

    @FindBy(xpath = BRAIN_CANCER_TYPE_MONTH_OF_DIAGNOSIS_XPATH)
    private WebElement brainOtherCancerMonthOfDiagnosis;

    @FindBy(xpath = ADD_ANOTHER_CHANGE_IN_TYPE_BUTTON_XPATH)
    private WebElement addAnotherChangeInTypeButton;

    @FindBy(xpath = BRAIN_CANCER_GRADE_CHANGED_YES_XPATH)
    private WebElement brainCancerGradeChangedYes;

    @FindBy(xpath = BRAIN_CANCER_GRADE_CHANGED_NO_XPATH)
    private WebElement brainCancerGradeChangedNo;

    @FindBy(xpath = BRAIN_CANCER_GRADE_CHANGED_UNSURE_HOW_TO_ANSWER_XPATH)
    private WebElement brainCancerGradeChangedUnsure;

    @FindBy(xpath = BRAIN_CANCER_GRADE_XPATH)
    private WebElement brainCancerChangedGrade;

    @FindBy(xpath = BRAIN_CANCER_YEAR_OF_GRADE_CHANGE_XPATH)
    private WebElement brainCancerYearOfGradeChange;

    @FindBy(xpath = BRAIN_CANCER_MONTH_OF_GRADE_CHANGE_XPATH)
    private WebElement brainCancerMonthOfGradeChange;

    @FindBy(xpath = ADD_ANOTHER_CHANGE_IN_GRADE_BUTTON_XPATH)
    private WebElement addAnotherChangeInGradeButton;

    @FindBy(xpath = YEAR_OF_BIRTH_XPATH)
    private WebElement birthYear;

    @FindBy(xpath = COUNTRY_OF_RESIDENCE_XPATH)
    private WebElement countryOfResidence;

    @FindBy(xpath = RESIDENTIAL_ZIPCODE_XPATH)
    private WebElement residentialZipcode;

    @FindBy(xpath = REFERRAL_SOURCE_XPATH)
    private WebElement referralSource;

    @FindBy(xpath = SUBMIT_BUTTON_XPATH)
    private WebElement submit;

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
    private static final String CONDITIONAL_SPECIFIER = "[2]";
    private static final String YES = "Yes";
    private static final String NO = "No";
    private static final String UNSURE_HOW_TO_ANSWER = "Unsure how to answer";
    private static final String MALE = "Male";
    private static final String FEMALE = "Female";
    private static final String INTERSEX = "Intersex";
    private static final String NON_BINARY_THIRD_GENDER = "Non-binary/third gender";
    private static final String PREFER_TO_SELF_DESCRIBE = "Prefer to self-describe";
    private static final String PREFER_NOT_TO_SAY = "Prefer not to say";


    public void waitUntilContentsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(generalContent));
        shortWait.until(ExpectedConditions.visibilityOf(submit));

        Assert.assertTrue(generalContent.isDisplayed());
    }

    //Question 1 - When were you first diagnosed with brain cancer? [Month]
    public void selectMonthOfFirstDiagnosis(String month) {
        JDIPageUtils.scrollDownToElement(FIRST_DIAGNOSED_DATE_CONTENT_XPATH);
        //JDIPageUtils.selectDropdownMenuOptionUsingOptionName(month, firstDiagnosedMonth);
        Select monthDropdown = new Select(firstDiagnosedMonth);
        monthDropdown.selectByVisibleText(month);
    }

    //Question 1 - When were you first diagnosed with brain cancer? [Year]
    public void selectYearOfFirstDiagnosis(String year) {
        //JDIPageUtils.selectDropdownMenuOptionUsingOptionName(year, firstDiagnosedYear);
        Select yearDropdown = new Select(firstDiagnosedYear);
        yearDropdown.selectByVisibleText(year);
    }

    //Question 2 - When you were first diagnosed with brain cancer, what type was it?
    public void inputTypeOfInitialCancerDiagnosis(String diagnosis) {
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_INITIAL_DIAGNOSIS_TEXTFIELD_XPATH);
        JDIPageUtils.inputText(brainCancerInitialDiagnosis, diagnosis);
        brainCancerInitialDiagnosis.sendKeys(Keys.ESCAPE);
    }

    //Question 3 - When you were first diagnosed with brain cancer, what grade was it?
    public void selectGradeAtTimeOfDiagnosis(String grade) {
        JDIPageUtils.scrollDownToElement(GRADE_DIAGNOSIS_OF_DIFFERENT_TYPE_OF_BRAIN_CANCER);
        Select gradeDropdown = new Select(gradeAtDiagnosis);
        gradeDropdown.selectByVisibleText(grade);
    }

    //Question 4 - Since your first diagnosis, has the type of your brain cancer changed?
    public void setHaveHadTypeOfBrainCancerChanged(String response) {
        //Radio buttons are horizontal so only one webelement is needed to scroll to all three choices
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_CHANGED_YES_XPATH);

        if (response.equalsIgnoreCase(YES)) {
            JDIPageUtils.clickUsingJavaScript(brainCancerChangedYes);

        } else if (response.equalsIgnoreCase(NO)) {
            JDIPageUtils.clickUsingJavaScript(brainCancerChangedNo);

        } else if (response.equalsIgnoreCase(UNSURE_HOW_TO_ANSWER)) {
            JDIPageUtils.clickUsingJavaScript(brainCancerChangedUnsure);
        }
    }

    //Question 4a - Were you diagnosed with a different type of brain cancer?
    public void setOtherBrainCancerType(String otherTypeOfBrainCancer) {
        shortWait.until(ExpectedConditions.visibilityOf(brainOtherCancerType));
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_TYPE_XPATH);
        JDIPageUtils.inputText(brainOtherCancerType, otherTypeOfBrainCancer);
        brainOtherCancerType.sendKeys(Keys.ESCAPE);
    }

    public void setYearOfOtherBrainCancerDiagnosis(String year) {
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_TYPE_YEAR_OF_DIAGNOSIS_XPATH);
        Select yearDropdown = new Select(brainOtherCancerYearOfDiagnosis);
        yearDropdown.selectByVisibleText(year);
    }

    public void setMonthOfOtherBrainCancerDiagnosis(String month) {
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_TYPE_MONTH_OF_DIAGNOSIS_XPATH);
        Select monthDropdown = new Select(brainOtherCancerMonthOfDiagnosis);
        monthDropdown.selectByVisibleText(month);
    }

    public void clickAddAnotherChangeInType() {
        JDIPageUtils.scrollDownToElement(ADD_ANOTHER_CHANGE_IN_TYPE_BUTTON_XPATH);
        JDIPageUtils.clickButtonUsingJDI(ADD_ANOTHER_CHANGE_IN_TYPE_BUTTON_XPATH, XPATH);
    }

    //Question 5 - Since your first diagnosis, has the grade of your brain cancer changed?
    public void setGradeOfBrainCancerWasChanged(String response) {
        //Radio buttons are horizontal so only one webelement is needed to scroll to all three choices
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_GRADE_CHANGED_YES_XPATH);

        if (response.equalsIgnoreCase(YES)) {
            JDIPageUtils.clickUsingJavaScript(brainCancerGradeChangedYes);

        } else if (response.equalsIgnoreCase(NO)) {
            JDIPageUtils.clickUsingJavaScript(brainCancerGradeChangedNo);

        } else if (response.equalsIgnoreCase(UNSURE_HOW_TO_ANSWER)) {
            JDIPageUtils.clickUsingJavaScript(brainCancerGradeChangedUnsure);
        }
    }

    //Question 5a - What grade did your brain cancer change to and when?
    public void setGradeOfBrainCancerAfterChange(String grade) {
        shortWait.until(ExpectedConditions.visibilityOf(brainCancerChangedGrade));
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_GRADE_XPATH);
        Select gradeDropdown = new Select(brainCancerChangedGrade);
        gradeDropdown.selectByVisibleText(grade);
    }

    public void setYearOfBrainCancerGradeChange(String year) {
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_YEAR_OF_GRADE_CHANGE_XPATH);
        Select yearDropdown = new Select(brainCancerYearOfGradeChange);
        yearDropdown.selectByVisibleText(year);
    }

    public void setMonthOfBrainCancerGradeChange(String month) {
        JDIPageUtils.scrollDownToElement(BRAIN_CANCER_MONTH_OF_GRADE_CHANGE_XPATH);
        Select monthDropdown = new Select(brainCancerMonthOfGradeChange);
        monthDropdown.selectByVisibleText(month);
    }

    public void clickAddAnotherChangeInGrade() {
        JDIPageUtils.scrollDownToElement(ADD_ANOTHER_CHANGE_IN_GRADE_BUTTON_XPATH);
        JDIPageUtils.clickButtonUsingJDI(ADD_ANOTHER_CHANGE_IN_GRADE_BUTTON_XPATH, XPATH);
    }

    //Question 6 - In what year were you born?
    public void selectYearOfBirth(String year) {
        JDIPageUtils.scrollDownToElement(YEAR_OF_BIRTH_XPATH);
        shortWait.until(ExpectedConditions.visibilityOf(birthYear));
        Select yearDropdown = new Select(birthYear);
        yearDropdown.selectByVisibleText(year);
    }

    //Question 7 - What country do you live in?
    public void selectCountryOfResidence(String country) {
        JDIPageUtils.scrollDownToElement(COUNTRY_OF_RESIDENCE_XPATH);
        Select countryDropdown = new Select(countryOfResidence);
        countryDropdown.selectByVisibleText(country);
    }

    //Question 8 - What is your ZIP or postal code?
    public void inputZipcode(String zipcode) {
        JDIPageUtils.scrollDownToElement(RESIDENTIAL_ZIPCODE_XPATH);
        JDIPageUtils.inputText(residentialZipcode, zipcode);
    }

    //Question 9 - How did you hear about the project?
    public void inputReferralSource(String source) {
        JDIPageUtils.scrollDownToElement(REFERRAL_SOURCE_XPATH);
        JDIPageUtils.inputText(referralSource, source);
    }

    public void clickSubmit() {
        shortWait.until(ExpectedConditions.visibilityOf(submit));
        JDIPageUtils.scrollDownToElement(SUBMIT_BUTTON_XPATH);

        //todo Investigate why the automated test needs to double click to succeed here
        JDIPageUtils.clickButtonUsingJDI(SUBMIT_BUTTON_XPATH, XPATH);
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
