package org.broadinstitute.ddp.pages.basilpages;

import java.util.Arrays;
import java.util.List;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByTag;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByText;
import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrequalifierPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(PrequalifierPage.class);
    //logger.info("[PREQUALIFIER]");

    private static final String TITLE_TEXT = "Join the Basil Research Study!";
    private static final String INTRO_TEXT_RELATIVE_LOCATION = "(//ddp-activity//div[@class='ng-star-inserted'])[2]";
    private static final String GENERAL_CONTENT_TAG = "ddp-activity";
    private static final String LIVES_IN_USA_STABLE_ID = "*[data-ddp-test*=BASIL_Q_STATES]";
    private static final String COUNTRY_OF_RESIDENCE_STABLE_ID = "//*[@data-ddp-test='answer:BASIL_Q_OTHER_COUNTRY']";
    private static final String IS_FEMALE_STABLE_ID = "*[data-ddp-test*=BASIL_Q_AGE]";
    private static final String HAS_BEEN_DIAGNOSED_STABLE_ID = "*[data-ddp-test*=BASIL_Q_DIAGNOSED]";
    private static final String TWENTY_ONE_TO_THIRTY_FOUR_YEARS_OLD = "21- to 34-years old ";
    private static final String THIRTY_FIVE_TO_FOURTY_FOUR_YEARS_OLD = "35- to 44-years old ";
    private static final String FOURTY_FIVE_TO_FIFTY_NINE_YEARS_OLD = "45- to 59-years old ";
    private static final String SIXTY_TO_SEVENTY_FOUR_YEARS_OLD = "60- to 74-years old ";
    private static final String SEVENTY_FIVE_YEARS_OLD_AND_UP = "75-years old and up";
    private static final String AGE_RANGE_TAG = "ddp-activity-radiobuttons-picklist-question";
    private static final String AGE_RANGE_VALIDATION_XPATH = "//ddp-activity-picklist-answer/following-sibling::"
            + "div//ddp-validation-message";
    private static final String VALIDATION_MESSAGE_TAG = "ddp-validation-message";
    private static final String HEARD_ABOUT_US_STABLE_ID = "//*[@data-ddp-test='answer:BASIL_Q_HOW_HEAR']";
    private static final String HEARD_ABOUT_US_OPTIONS_TAG = "mat-option";
    private static final String ANSWER_TRUE_XPATH = "//*[text()='Yes']";
    private static final String ANSWER_FALSE_XPATH = "//*[text()='No']";
    private static final String SUBMIT_BUTTON_XPATH = "//button[normalize-space(text()) = 'SUBMIT']";

    //Picklist options
    private static final List<String> PICKLIST_AGE_RANGE_OPTIONS = Arrays.asList(TWENTY_ONE_TO_THIRTY_FOUR_YEARS_OLD,
            THIRTY_FIVE_TO_FOURTY_FOUR_YEARS_OLD,
            FOURTY_FIVE_TO_FIFTY_NINE_YEARS_OLD,
            SIXTY_TO_SEVENTY_FOUR_YEARS_OLD,
            SEVENTY_FIVE_YEARS_OLD_AND_UP);

    private static final List<String> PICKLIST_HEARD_ABOUT_US_OPTIONS = Arrays.asList("From my healthcare provider",
            "From another participant in the study",
            "From friends/family not in study",
            "From an ad",
            "By searching online");

    @ByText(TITLE_TEXT)
    private WebElement title;

    @FindBy(xpath = INTRO_TEXT_RELATIVE_LOCATION)
    private WebElement introduction;

    @ByTag(GENERAL_CONTENT_TAG)
    private WebElement prequalifierContent;

    @FindBy(xpath = SUBMIT_BUTTON_XPATH)
    private Button submit;

    @FindBy(css = LIVES_IN_USA_STABLE_ID)
    private WebElement liveInUSA;

    @FindBy(xpath = COUNTRY_OF_RESIDENCE_STABLE_ID)
    private WebElement otherCountryText;

    @FindBy(css = IS_FEMALE_STABLE_ID)
    private WebElement isAFemale;

    @FindBy(css = HAS_BEEN_DIAGNOSED_STABLE_ID)
    private WebElement hasBeenDiagnosed;

    //To be used when webdriver says it cannot find above ageRange webelement
    //Xpath used above checks out fine on broswer console via JavaScript
    @ByTag(AGE_RANGE_TAG)
    private WebElement ageRangeTag;

    @FindBy(xpath = AGE_RANGE_VALIDATION_XPATH)
    private WebElement ageRangeValidationError;

    @ByTag(VALIDATION_MESSAGE_TAG)
    private List<WebElement> validationErrors;

    @FindBy(xpath = HEARD_ABOUT_US_STABLE_ID)
    private WebElement heardAboutUs;

    private boolean currentlyLivesInUSA;

    public boolean doesLiveInUSA() {
        return currentlyLivesInUSA;
    }

    public void setLiveInUSA(boolean userLivesInUsa) {
        currentlyLivesInUSA = userLivesInUsa;
        JDIPageUtils.selectRadioButtonOption(liveInUSA, userLivesInUsa);
    }

    public void verifyLiveInUSAAnswerSelected(boolean userLivesInUsa) {
        if (userLivesInUsa) {
            JDIPageUtils.verifyRadioButtonAnswerIsSelected(liveInUSA, ANSWER_TRUE_XPATH);
        } else {
            JDIPageUtils.verifyRadioButtonAnswerIsSelected(liveInUSA, ANSWER_FALSE_XPATH);
        }
    }

    public void setCountryOfResidence(String countryOfResidence) {
        if (!currentlyLivesInUSA) {
            JDIPageUtils.inputText(otherCountryText, countryOfResidence);
        } else {
            throw new RuntimeException("You cannot answer this question if you already live in the USA!");
        }
    }

    public void setGender(boolean isFemale) {
        JDIPageUtils.selectRadioButtonOption(isAFemale, isFemale);
    }

    public void setHaveBeenDiagnosed(boolean userHasBeenDiagnosed) {
        JDIPageUtils.selectRadioButtonOption(hasBeenDiagnosed, userHasBeenDiagnosed);
    }

    public void selectHowHearAboutUs(String howHeard) {
        if (PICKLIST_HEARD_ABOUT_US_OPTIONS.contains(howHeard)) {
            JDIPageUtils.selectOptionFromMultipleChoiceDropdownMenu(HEARD_ABOUT_US_STABLE_ID,
                    HEARD_ABOUT_US_OPTIONS_TAG,
                    howHeard);
        } else {
            throw new RuntimeException("Please enter a valid option concerning how you heard about the study.");
        }
    }

    public void verifyHowHeardAboutStudyOptionUnselected(String option) {
        if (PICKLIST_HEARD_ABOUT_US_OPTIONS.contains(option)) {
            JDIPageUtils.verifyDropDownOptionUnselected(HEARD_ABOUT_US_STABLE_ID,
                    HEARD_ABOUT_US_OPTIONS_TAG,
                    option);
        } else {
            throw new RuntimeException("Please enter a valid option to verify.");
        }
    }

    public void verifyHowHeardAboutStudySelectedAnswers(String... answers) {
        if (PICKLIST_HEARD_ABOUT_US_OPTIONS.containsAll(Arrays.asList(answers))) {
            JDIPageUtils.verifyDropDownOptionsSelected(HEARD_ABOUT_US_STABLE_ID,
                    HEARD_ABOUT_US_OPTIONS_TAG,
                    answers);
        } else {
            throw new RuntimeException("Please select valid answers");
        }
    }

    public void selectUserAgeRange(String age) {
        if (PICKLIST_AGE_RANGE_OPTIONS.contains(age)) {
            logger.info("Selecting user age range...");
            JDIPageUtils.selectRadioButton(age);
        } else {
            throw new RuntimeException("Please enter a valid age range to check for.");
        }
    }

    public void verifyAgeRangeSelected(String age) {
        if (PICKLIST_AGE_RANGE_OPTIONS.contains(age)) {
            JDIPageUtils.verifyRadioButtonSelected(age);
        } else {
            throw new RuntimeException("Please enter a valid age range to check for.");
        }
    }

    public void verifyAgeRangeUnselected(String age) {
        if (PICKLIST_AGE_RANGE_OPTIONS.contains(age)) {
            JDIPageUtils.verifyRadioButtonUnSelected(age);
        } else {
            throw new RuntimeException("Please enter a valid age range to check for.");
        }
    }

    public void verifyAgeRangeValidationErrorDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(ageRangeValidationError));
        Assert.assertTrue(ageRangeValidationError.isDisplayed());
    }

    public void verifyNoValidationMessagesDisplayed() {
        Assert.assertTrue(validationErrors.isEmpty());
    }

    /**
     * Verify the non-hidden, non-conditional questions are shown upon seeing the prequalifier
     */
    public void waitUntilPrequalifierQuestionsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(liveInUSA));
        shortWait.until(ExpectedConditions.visibilityOf(isAFemale));
        shortWait.until(ExpectedConditions.visibilityOf(hasBeenDiagnosed));
    }

    public void clickSubmit() {
        waitUntilPrequalifierElementsAreDisplayed();
        JDIPageUtils.clickUsingJavaScript(submit.getWebElement());
    }

    private void waitUntilPrequalifierElementsAreDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(title));
        shortWait.until(ExpectedConditions.visibilityOf(introduction));
        shortWait.until(ExpectedConditions.visibilityOf(prequalifierContent));
    }

    private void waitUntilPrequalifierElementsAreNotDisplayed() {
        shortWait.until(ExpectedConditions.invisibilityOf(title));
        shortWait.until(ExpectedConditions.invisibilityOf(introduction));
        shortWait.until(ExpectedConditions.invisibilityOf(prequalifierContent));
    }

    public void assertPrequalifierElementsAreDisplayed() {
        waitUntilPrequalifierElementsAreDisplayed();
        Assert.assertTrue(title.isDisplayed());
        Assert.assertTrue(introduction.isDisplayed());
        Assert.assertTrue(prequalifierContent.isDisplayed());
        Assert.assertTrue(submit.isDisplayed());
    }

    /**
     * Waits until url is reached (and previous url is finished) before checking.
     * urlTemplate is set at BasilAppSite.java for each page object
     */
    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        waitUntilPrequalifierElementsAreDisplayed();
    }
}
