package org.broadinstitute.ddp.pages.sandboxpages;

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

public class UserProfilePage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(UserProfilePage.class);

    private static final String USER_PROFILE_BUTTON_XPATH = "//*[@data-ddp-test='profileButton']";
    private static final String USER_PROFILE_POPUP_TAG = "ddp-user-preferences";
    private static final String MONTH_DROPDOWN_XPATH = "//*[@data-ddp-test='birthday::month']";
    private static final String DAY_DROPDOWN_XPATH = "//*[@data-ddp-test='birthday::day']";
    private static final String YEAR_DROPDOWN_XPATH = "//*[@data-ddp-test='birthday::year']";
    private static final String SEX_DROPDOWN_XPATH = "//*[@data-ddp-test='gender']";
    private static final String PREFERRED_LANGUAGE_DROPDOWN_XPATH = "//*[@data-ddp-test='locale']";
    private static final String SAVE_BUTTON_TEXT = "SAVE";
    private static final String CANCEL_BUTTON_XPATH = "//*[@data-ddp-test='cancelButton']";
    private static final String MONTH_CAPTION = "Month";
    private static final String DAY_CAPTION = "Date";
    private static final String YEAR_CAPTION = "Year";
    private static final String SEX_CAPTION = "Sex";
    private static final String PREFERRED_LANGUAGE_CAPTION = "Preferred language";
    private static final String DROPDOWN_OPTIONS_TAG = "mat-option";
    private static final String INVALID_DATE_XPATH = "//ddp-validation-message[@message='invalid date']";
    //List of options
    private static final String OPTION_MALE = "MALE";
    private static final String OPTION_FEMALE = "FEMALE";
    private static final String OPTION_INTERSEX = "INTERSEX";
    private static final String OPTION_PREFER_NOT_TO_ANSWER = "PREFER_NOT_TO_ANSWER";
    private static final List<String> SEX_OPTIONS = Arrays.asList(OPTION_MALE,
            OPTION_FEMALE,
            OPTION_INTERSEX,
            OPTION_PREFER_NOT_TO_ANSWER);

    enum Sex {
        MALE("Male"),
        FEMALE("Female"),
        INTERSEX("Intersex"),
        PREFER_NOT_TO_ANSWER("Prefer not to answer");

        private final String uiText;

        Sex(String uiText) {
            this.uiText = uiText;
        }

        String getUiDescription() {
            return uiText;
        }
    }

    //Value of preferred language must be in language code; Options are English, Russian, and French respectively
    private static final String OPTION_ENGLISH = "en";
    private static final String OPTION_RUSSIAN = "ru";
    private static final String OPTION_FRENCH = "fr";
    private static final List<String> PREFERRED_LANGUAGE_OPTIONS = Arrays.asList(OPTION_ENGLISH,
            OPTION_RUSSIAN,
            OPTION_FRENCH);

    enum PreferredLanguage {
        EN("English"),
        RU("Russian"),
        FR("French");

        private final String uiText;

        PreferredLanguage(String uiText) {
            this.uiText = uiText;
        }

        String getUiDescription() {
            return uiText;
        }
    }

    @FindBy(xpath = USER_PROFILE_BUTTON_XPATH)
    private Button userProfile;

    @ByTag(USER_PROFILE_POPUP_TAG)
    private WebElement userProfilePopup;

    @FindBy(xpath = MONTH_DROPDOWN_XPATH)
    private WebElement month;

    @FindBy(xpath = DAY_DROPDOWN_XPATH)
    private WebElement day;

    @FindBy(xpath = YEAR_DROPDOWN_XPATH)
    private WebElement year;

    @FindBy(xpath = SEX_DROPDOWN_XPATH)
    private WebElement sex;

    @FindBy(xpath = PREFERRED_LANGUAGE_DROPDOWN_XPATH)
    private WebElement preferredLanguage;

    @FindBy(xpath = INVALID_DATE_XPATH)
    private WebElement invalidDateWarning;

    @ByText(SAVE_BUTTON_TEXT)
    private Button save;

    @FindBy(xpath = CANCEL_BUTTON_XPATH)
    private Button cancel;

    public void verifyUserProfileButtonDsplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(userProfile.getWebElement()));
        Assert.assertTrue(userProfile.isDisplayed());
    }

    public void verifyUserProfileButtonCaption(String caption) {
        shortWait.until(ExpectedConditions.visibilityOf(userProfile.getWebElement()));
        String buttonText = JDIPageUtils.getWebElementTextWithoutIcon(userProfile.getWebElement());
        Assert.assertEquals(caption, buttonText);
    }

    public void clickUserProfile() {
        shortWait.until(ExpectedConditions.visibilityOf(userProfile.getWebElement()));
        userProfile.click();
    }

    public void verifyUserProfilePopupDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(userProfilePopup));
        shortWait.until(ExpectedConditions.visibilityOf(month));
        shortWait.until(ExpectedConditions.visibilityOf(day));
        shortWait.until(ExpectedConditions.visibilityOf(year));
        shortWait.until(ExpectedConditions.visibilityOf(sex));
        shortWait.until(ExpectedConditions.visibilityOf(preferredLanguage));

        Assert.assertTrue(userProfilePopup.isDisplayed());
    }

    public void verifyMonthCaption() {
        shortWait.until(ExpectedConditions.visibilityOf(month));
        String placeholderText = JDIPageUtils.getDropDownWebElementPlaceholderText(MONTH_DROPDOWN_XPATH);
        Assert.assertEquals(MONTH_CAPTION, placeholderText);
    }

    public void setBirthInformation(String birthdate) {
        String month = JDIPageUtils.parseBirthdateInformation(birthdate, MONTH_CAPTION);
        String day = JDIPageUtils.parseBirthdateInformation(birthdate, DAY_CAPTION);
        String year = JDIPageUtils.parseBirthdateInformation(birthdate, YEAR_CAPTION);

        setBirthMonth(month);
        setBirthDate(day);
        setBirthYear(year);
    }

    private void setBirthMonth(String birthMonth) {
        verifyMonthCaption();
        JDIPageUtils.selectOptionFromSingleChoiceDropDownMenu(MONTH_DROPDOWN_XPATH, DROPDOWN_OPTIONS_TAG, birthMonth);
    }

    public void verifyDateCaption() {
        shortWait.until(ExpectedConditions.visibilityOf(day));
        String placeholderText = JDIPageUtils.getDropDownWebElementPlaceholderText(DAY_DROPDOWN_XPATH);
        Assert.assertEquals(DAY_CAPTION, placeholderText);
    }

    private void setBirthDate(String birthDate) {
        verifyDateCaption();
        JDIPageUtils.selectOptionFromSingleChoiceDropDownMenu(DAY_DROPDOWN_XPATH, DROPDOWN_OPTIONS_TAG, birthDate);
    }

    public void verifyYearCaption() {
        shortWait.until(ExpectedConditions.visibilityOf(year));
        String placeholderText = JDIPageUtils.getDropDownWebElementPlaceholderText(YEAR_DROPDOWN_XPATH);
        Assert.assertEquals(YEAR_CAPTION, placeholderText);
    }

    private void setBirthYear(String birthYear) {
        verifyYearCaption();
        JDIPageUtils.selectOptionFromSingleChoiceDropDownMenu(YEAR_DROPDOWN_XPATH, DROPDOWN_OPTIONS_TAG, birthYear);
    }

    public void verifyBirthInformationSetting(String birthdate) {
        String month = JDIPageUtils.parseBirthdateInformation(birthdate, MONTH_CAPTION);
        String day = JDIPageUtils.parseBirthdateInformation(birthdate, DAY_CAPTION);
        String year = JDIPageUtils.parseBirthdateInformation(birthdate, YEAR_CAPTION);

        verifyBirthMonthSetting(month);
        verifyBirthDateSetting(day);
        verifyBirthYearSetting(year);
    }

    private void verifyBirthMonthSetting(String birthMonth) {
        JDIPageUtils.verifyDropDownOptionSelected(month, birthMonth);
    }

    private void verifyBirthDateSetting(String birthDate) {
        JDIPageUtils.verifyDropDownOptionSelected(day, birthDate);
    }

    private void verifyBirthYearSetting(String birthYear) {
        JDIPageUtils.verifyDropDownOptionSelected(year, birthYear);
    }

    public void verifySexCaption() {
        shortWait.until(ExpectedConditions.visibilityOf(sex));
        String placeholderText = JDIPageUtils.getDropDownWebElementPlaceholderText(SEX_DROPDOWN_XPATH);
        Assert.assertEquals(SEX_CAPTION, placeholderText);
    }

    public void setSex(String sexOfUser) {
        verifySexCaption();
        if (SEX_OPTIONS.contains(sexOfUser)) {
            sexOfUser = translateUserChoiceToUIDescription(sexOfUser);
            JDIPageUtils.selectOptionFromSingleChoiceDropDownMenu(SEX_DROPDOWN_XPATH, DROPDOWN_OPTIONS_TAG, sexOfUser);
        } else {
            throw new RuntimeException("Please enter a valid option.");
        }
    }

    public void verifySexSetting(String sexOfUser) {
        sexOfUser = translateUserChoiceToUIDescription(sexOfUser);
        JDIPageUtils.verifyDropDownOptionSelected(sex, sexOfUser);
    }

    public void verifyPreferredLanguageCaption() {
        shortWait.until(ExpectedConditions.visibilityOf(preferredLanguage));
        String placeholderText = JDIPageUtils.getDropDownWebElementPlaceholderText(PREFERRED_LANGUAGE_DROPDOWN_XPATH);
        Assert.assertEquals(PREFERRED_LANGUAGE_CAPTION, placeholderText);
    }

    public void setPreferredLanguage(String language) {
        verifyPreferredLanguageCaption();
        if (PREFERRED_LANGUAGE_OPTIONS.contains(language)) {
            language = translateUserChoiceToUIDescription(language);
            JDIPageUtils.selectOptionFromSingleChoiceDropDownMenu(PREFERRED_LANGUAGE_DROPDOWN_XPATH,
                    DROPDOWN_OPTIONS_TAG,
                    language);
        } else {
            throw new RuntimeException("Please enter a valid language option.");
        }

    }

    public void verifyPreferredLanguageSetting(String language) {
        language = translateUserChoiceToUIDescription(language);
        JDIPageUtils.verifyDropDownOptionSelected(preferredLanguage, language);
    }

    private String translateUserChoiceToUIDescription(String userChoice) {
        String translatedChoice = null;
        //To match the choices as stored in enumerators
        String choice = userChoice.toUpperCase();

        if (SEX_OPTIONS.contains(userChoice)) {
            //Translate user choice to text shown on UI e.g. change PREFER_NOT_TO_ANSWER => Prefer not to answer
            Sex sexOption = Sex.valueOf(choice);
            translatedChoice = sexOption.getUiDescription();

        } else if (PREFERRED_LANGUAGE_OPTIONS.contains(userChoice)) {
            //Translate user choice to text shown on UI e.g. change fr => French
            PreferredLanguage preferredLanguageOption = PreferredLanguage.valueOf(choice);
            translatedChoice = preferredLanguageOption.getUiDescription();
        }
        return translatedChoice;
    }

    public void verifyInvalidDateWarningDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(invalidDateWarning));
        Assert.assertTrue(invalidDateWarning.isDisplayed());
    }

    public void clickSave() {
        shortWait.until(ExpectedConditions.visibilityOf(save.getWebElement()));
        save.click();
        shortWait.until(ExpectedConditions.invisibilityOf(userProfilePopup));
    }

    public void clickCancel() {
        shortWait.until(ExpectedConditions.visibilityOf(cancel.getWebElement()));
        cancel.click();
        shortWait.until(ExpectedConditions.invisibilityOf(userProfilePopup));
    }

    public void exitUserProfile() {
        if (userProfilePopup.isDisplayed()) {
            clickCancel();
        }
    }

    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
    }
}
