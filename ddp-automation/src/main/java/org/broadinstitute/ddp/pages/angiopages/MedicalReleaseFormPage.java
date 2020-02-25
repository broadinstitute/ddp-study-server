package org.broadinstitute.ddp.pages.angiopages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MedicalReleaseFormPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(MedicalReleaseFormPage.class);

    //Medicial release form content
    private static final String MEDICAL_RELEASE_FORM_CONTENT_XPATH = "//article[contains(@class, 'PageContent')]";

    //Subtitle containing email and phone number
    private static final String HELP_INFORMATION_SUBTITLE = "//div[contains(@class, 'subtitle')]";

    //User's full name
    private static final String USER_FULL_NAME_XPATH = "//mat-form-field//input[@formcontrolname='name']";

    //Address related webcontents
    private static final String COUNTRY_TERRITORY_ADDRESS_DROPDOWN_MENU_XPATH = "//mat-select[@aria-label='Country/Territory']";
    private static final String COUNTRY_TERRITORY_ADDRESS_DROPDOWN_MENU_OPTIONS_XPATH = "//div[@id='cdk-overlay-0']";
    private static final String STREET_ADDRESS_INPUT_XPATH = "//mat-form-field//input[@placeholder='Street Address']";
    private static final String STREET_ADDRESS_OPTIONAL_INPUT_XPATH = "//mat-form-field//input[@formcontrolname='street2']";
    private static final String CITY_ADDRESS_INPUT_XPATH = "//mat-form-field//input[@formcontrolname='city']";
    private static final String STATE_ADDRESS_DROPDOWN_MENU_XPATH = "//mat-select[@formcontrolname='state']";
    private static final String STATE_ADDRESS_DROPDOWN_MENU_OPTIONS_XPATH = "//div[@id='cdk-overlay-1']";
    private static final String ZIPCODE_ADDRESS_INPUT_XPATH = "//mat-form-field//input[@formcontrolname='zip']";
    private static final String PHONE_NUMBER_INPUT_XPATH = "//mat-form-field//input[@formcontrolname='phone']";


    //Address suggestion box
    private static final String ADDRESS_SUGGESTION_BOX_CONTENT_XPATH = "//mat-card";
    private static final String ADDRESS_SUGGESTED_OPTION_XPATH = "//mat-radio-group[@formcontrolname='suggestionRadioGroup']"
            + "//mat-radio-button[@value='suggested']";
    private static final String ADDRESS_AS_ENTERED_OPTION_XPATH = "//mat-radio-group[@formcontrolname='suggestionRadioGroup']"
            + "//mat-radio-button[@value='original']";

    //Physician address auto-complete
    private static final String INSTITUTION_ADDRESS_AUTOCOMPLETE_SUGGESTIONS_XPATH = "//div[contains(@id, 'autocomplete')]";

    //User's general/main physician and institution webelements
    private static final String ADD_ANOTHER_PHYSICIAN_BUTTON_XPATH = "//button[contains(text(), 'ADD ANOTHER PHYSICIAN')]";
    private static final String GENERAL_PHYSICIAN_NAME_INPUT_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 2)]"
            + "//input[@placeholder='Physician Name']";
    private static final String GENERAL_INSTITUTION_NAME_INPUT_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 2)]"
            + "//input[@placeholder='Institution (if any)']";
    private static final String GENERAL_INSTITUTION_CITY_INPUT_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 2)]"
            + "//input[@placeholder='City']";
    private static final String GENERAL_INSTITUTION_STATE_INPUT_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 2)]"
            + "//input[@placeholder='State']";

    //User's initial biopsy webelements
    private static final String INITIAL_BIOPSY_INSTITUTION_INPUT_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 3)]"
            + "//input[@placeholder='Institution']";
    private static final String INITIAL_BIOPSY_INSTITUTION_CITY_INPUT_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 3)]"
            + "//input[@placeholder='City']";
    private static final String INITIAL_BIOPSY_INSTITUTION_STATE_INPUT_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 3)]"
            + "//input[@placeholder='State']";

    //User's additional biopsies or surgeries
    private static final String ADD_ANOTHER_INSTITUTION_BUTTON_XPATH = "//button[contains(text(), 'ADD ANOTHER INSTITUTION')]";
    private static final String ADDITIONAL_BIOPSIES_OR_SURGERIES_INSTITUTION_XPATH = ""
            + "//li[contains(@class, 'ddp-li') and contains(@value, 4)]"
            + "//input[@placeholder='Institution']";
    private static final String ADDITIONAL_BIOPSIES_OR_SURGERIES_CITY_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 4)]"
            + "//input[@placeholder='City']";
    private static final String ADDITIONAL_BIOPSIES_OR_SURGERIES_STATE_XPATH = "//li[contains(@class, 'ddp-li') and contains(@value, 4)]"
            + "//input[@placeholder='State']";

    //Release agreement
    private static final String RELEASE_AGREEMENT_CHECKBOX_XPATH = "//div[contains(@class, 'ddp-agreement')]//input";

    private static final String SUBMIT_BUTTON_XPATH = "//button[normalize-space(text()) = 'SUBMIT']";


    @FindBy(xpath = MEDICAL_RELEASE_FORM_CONTENT_XPATH)
    private WebElement medicalReleaseFormContent;

    @FindBy(xpath = HELP_INFORMATION_SUBTITLE)
    private WebElement helpInformationSubtitle;

    @FindBy(xpath = USER_FULL_NAME_XPATH)
    private WebElement userFullName;

    @FindBy(xpath = COUNTRY_TERRITORY_ADDRESS_DROPDOWN_MENU_XPATH)
    private WebElement addressCountryOrUSTerritoryDropdownMenu;

    @FindBy(xpath = COUNTRY_TERRITORY_ADDRESS_DROPDOWN_MENU_OPTIONS_XPATH)
    private WebElement addressCountryOrUsTerritoryOptions;

    @FindBy(xpath = STREET_ADDRESS_INPUT_XPATH)
    private WebElement addressStreetMain;

    @FindBy(xpath = STREET_ADDRESS_OPTIONAL_INPUT_XPATH)
    private WebElement addressStreetOptional;

    @FindBy(xpath = CITY_ADDRESS_INPUT_XPATH)
    private WebElement addressCity;

    @FindBy(xpath = STATE_ADDRESS_DROPDOWN_MENU_XPATH)
    private WebElement addressStateDropdownMenu;

    @FindBy(xpath = STATE_ADDRESS_DROPDOWN_MENU_OPTIONS_XPATH)
    private WebElement addressStateOptions;

    @FindBy(xpath = ZIPCODE_ADDRESS_INPUT_XPATH)
    private WebElement addressZipcode;

    @FindBy(xpath = PHONE_NUMBER_INPUT_XPATH)
    private WebElement phoneNumber;

    @FindBy(xpath = ADDRESS_SUGGESTION_BOX_CONTENT_XPATH)
    private WebElement addressSuggestionBox;

    @FindBy(xpath = ADDRESS_SUGGESTED_OPTION_XPATH)
    private WebElement suggestedAddressOption;

    @FindBy(xpath = ADDRESS_AS_ENTERED_OPTION_XPATH)
    private WebElement asEnteredAddressOption;

    @FindBy(xpath = ADD_ANOTHER_PHYSICIAN_BUTTON_XPATH)
    private WebElement addAnotherPhysicianButton;

    @FindBy(xpath = GENERAL_PHYSICIAN_NAME_INPUT_XPATH)
    private WebElement physicianName;

    @FindBy(xpath = GENERAL_INSTITUTION_NAME_INPUT_XPATH)
    private WebElement physicianInstitutionName;

    @FindBy(xpath = GENERAL_INSTITUTION_CITY_INPUT_XPATH)
    private WebElement physicianInstitutionCity;

    @FindBy(xpath = GENERAL_INSTITUTION_STATE_INPUT_XPATH)
    private WebElement physicianInstitutionState;

    @FindBy(xpath = INITIAL_BIOPSY_INSTITUTION_INPUT_XPATH)
    private WebElement initialBiopsyInstitutionName;

    @FindBy(xpath = INITIAL_BIOPSY_INSTITUTION_CITY_INPUT_XPATH)
    private WebElement initialBiopsyInstitutionCity;

    @FindBy(xpath = INITIAL_BIOPSY_INSTITUTION_STATE_INPUT_XPATH)
    private WebElement initialBiopsyInstitutionState;

    @FindBy(xpath = ADDITIONAL_BIOPSIES_OR_SURGERIES_INSTITUTION_XPATH)
    private WebElement additionalBiopsyOrSurgeryInstitution;

    @FindBy(xpath = ADDITIONAL_BIOPSIES_OR_SURGERIES_CITY_XPATH)
    private WebElement additionalBiopsyOrSurgeryCity;

    @FindBy(xpath = ADDITIONAL_BIOPSIES_OR_SURGERIES_STATE_XPATH)
    private WebElement additionalBiopsyOrSurgeryState;

    @FindBy(xpath = ADD_ANOTHER_INSTITUTION_BUTTON_XPATH)
    private Button addAnotherInstitutionButton;

    @FindBy(xpath = RELEASE_AGREEMENT_CHECKBOX_XPATH)
    private WebElement releaseAgreementCheckbox;

    @FindBy(xpath = SUBMIT_BUTTON_XPATH)
    private WebElement submit;


    public void setFullName(String fullName) {
        JDIPageUtils.scrollDownToElement(USER_FULL_NAME_XPATH);
        JDIPageUtils.inputText(userFullName, fullName);
    }

    public void setCountryOrUSTerritory(String country) {
        JDIPageUtils.scrollDownToElement(COUNTRY_TERRITORY_ADDRESS_DROPDOWN_MENU_XPATH);
        JDIPageUtils.selectDropdownMenuOptionUsingOptionName(country, addressCountryOrUSTerritoryDropdownMenu);
    }

    public void setMainStreetAddress(String streetAddress) {
        JDIPageUtils.scrollDownToElement(STREET_ADDRESS_INPUT_XPATH);
        JDIPageUtils.inputText(addressStreetMain, streetAddress);
        //The Tab key press is to get rid of the auto-complete suggestions
        addressStreetMain.sendKeys(Keys.TAB);
    }

    public void setOptionalStreetAddress(String streetAddress) {
        JDIPageUtils.scrollDownToElement(STREET_ADDRESS_OPTIONAL_INPUT_XPATH);
        JDIPageUtils.inputText(addressStreetOptional, streetAddress);
        //JDIPageUtils.inputUsingJavaScript(streetAddress, addressStreetOptional);
        //The Tab key press is to get rid of the auto-complete suggestions
        addressStreetOptional.sendKeys(Keys.TAB);
        addressStreetOptional.sendKeys(Keys.RETURN);
    }

    public void setCity(String city) {
        JDIPageUtils.scrollDownToElement(CITY_ADDRESS_INPUT_XPATH);
        JDIPageUtils.inputText(addressCity, city);
    }

    /**
     * To be used to set the province/state of the address e.g. a Canadian province or American state
     * @param state The name of the province
     */
    public void setState(String state) {
        JDIPageUtils.scrollDownToElement(STATE_ADDRESS_DROPDOWN_MENU_XPATH);
        String capitalizedState = state.toUpperCase();
        JDIPageUtils.selectDropdownMenuOptionUsingOptionName(capitalizedState, addressStateDropdownMenu);
    }

    public void setZipcode(String zipcode) {
        JDIPageUtils.scrollDownToElement(ZIPCODE_ADDRESS_INPUT_XPATH);
        JDIPageUtils.inputText(addressZipcode, zipcode);
    }

    public void setPhoneNumber(String number) {
        JDIPageUtils.scrollDownToElement(PHONE_NUMBER_INPUT_XPATH);
        JDIPageUtils.inputText(phoneNumber, number);
    }

    public void verifyAddressSuggestionBoxDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(addressSuggestionBox));
        JDIPageUtils.scrollDownToElement(ADDRESS_SUGGESTION_BOX_CONTENT_XPATH);
        Assert.assertTrue(addressSuggestionBox.isDisplayed());
    }

    /**
     * Choose whether to use the suggested address or the address as entered
     * By default, using true will select the option to use the suggested address
     * @param chooseToUseSuggestedAddress Set as true to use 'suggested' address, set as false to use 'as entered' address
     */
    public void chooseToUseAsEnteredAddressOrSuggestedAddress(Boolean chooseToUseSuggestedAddress) {
        if (chooseToUseSuggestedAddress) {
            clickSuggestedAddress();
        } else {
            clickAsEnteredAddress();
        }
    }

    private void clickSuggestedAddress() {
        JDIPageUtils.scrollDownToElement(ADDRESS_SUGGESTED_OPTION_XPATH);
        suggestedAddressOption.click();
    }

    private void clickAsEnteredAddress() {
        JDIPageUtils.scrollDownToElement(ADDRESS_AS_ENTERED_OPTION_XPATH);
        asEnteredAddressOption.click();
    }

    public void setPhysicianName(String name) {
        JDIPageUtils.scrollDownToElement(GENERAL_PHYSICIAN_NAME_INPUT_XPATH);
        JDIPageUtils.inputText(physicianName, name);
    }

    public void setPhysicianInstitutionName(String name) {
        JDIPageUtils.scrollDownToElement(GENERAL_INSTITUTION_NAME_INPUT_XPATH);
        physicianInstitutionName.click();
        JDIPageUtils.inputText(physicianInstitutionName, name);

        //Used to get rid of auto-complete suggestion box to allow for correct manual input
        //via automated test
        physicianInstitutionName.sendKeys(Keys.ESCAPE);
    }

    public void setPhysicianInstitutionCity(String city) {
        JDIPageUtils.scrollDownToElement(GENERAL_INSTITUTION_CITY_INPUT_XPATH);
        physicianInstitutionCity.click();
        JDIPageUtils.inputText(physicianInstitutionCity, city);
    }

    public void setPhysicianInstitutionState(String state) {
        JDIPageUtils.scrollDownToElement(GENERAL_INSTITUTION_STATE_INPUT_XPATH);
        physicianInstitutionState.click();
        JDIPageUtils.inputText(physicianInstitutionState, state);
    }

    public void clickAddAnotherPhysician() {
        addAnotherPhysicianButton.click();
    }

    public void setInstitutionWhereInitialBiopsyWasPerformed(String institution) {
        JDIPageUtils.scrollDownToElement(INITIAL_BIOPSY_INSTITUTION_INPUT_XPATH);
        initialBiopsyInstitutionName.click();
        JDIPageUtils.inputText(initialBiopsyInstitutionName, institution);
    }

    public void setCityWhereInitialBiopsyWasPerformed(String city) {
        JDIPageUtils.scrollDownToElement(INITIAL_BIOPSY_INSTITUTION_CITY_INPUT_XPATH);
        initialBiopsyInstitutionCity.click();
        JDIPageUtils.inputText(initialBiopsyInstitutionCity, city);
    }

    public void setStateWhereInitialBiopsyPerformed(String state) {
        JDIPageUtils.scrollDownToElement(INITIAL_BIOPSY_INSTITUTION_STATE_INPUT_XPATH);
        initialBiopsyInstitutionState.click();
        JDIPageUtils.inputText(initialBiopsyInstitutionState, state);
    }

    public void setInstitutionWhereAdditionalBiopsyOrSurgeryWasPerformed(String institution) {
        JDIPageUtils.scrollDownToElement(ADDITIONAL_BIOPSIES_OR_SURGERIES_INSTITUTION_XPATH);
        additionalBiopsyOrSurgeryInstitution.click();
        JDIPageUtils.inputText(additionalBiopsyOrSurgeryInstitution, institution);
    }

    public void setCityWhereAdditionalBiopsyOrSurgeryWasPerformed(String city) {
        JDIPageUtils.scrollDownToElement(ADDITIONAL_BIOPSIES_OR_SURGERIES_CITY_XPATH);
        additionalBiopsyOrSurgeryCity.click();
        JDIPageUtils.inputText(additionalBiopsyOrSurgeryCity, city);
    }

    public  void setStateWhereAdditionalBiopsyOrSurgeryWasPerformed(String state) {
        JDIPageUtils.scrollDownToElement(ADDITIONAL_BIOPSIES_OR_SURGERIES_STATE_XPATH);
        additionalBiopsyOrSurgeryState.click();
        JDIPageUtils.inputText(additionalBiopsyOrSurgeryState, state);
    }

    /**
     * Click this before using the following:
     * setInstitutionWhereAdditionalBiopsyOrSurgeryWasPerformed(String institution)
     * setCityWhereAdditionalBiopsyOrSurgeryWasPerformed(String city)
     * setStateWhereAdditionalBiopsyOrSurgeryWasPerformed(String state)
     * the fields that the above manipulate do not appear without clicking this button
     */
    public void clickAddAnotherInstitution() {
        JDIPageUtils.scrollDownToElement(ADD_ANOTHER_INSTITUTION_BUTTON_XPATH);
        addAnotherInstitutionButton.click();
    }

    public void clickReleaseAgreementCheckbox() {
        JDIPageUtils.scrollDownToElement(RELEASE_AGREEMENT_CHECKBOX_XPATH);
        shortWait.until(ExpectedConditions.visibilityOf(releaseAgreementCheckbox));
        JDIPageUtils.clickUsingJavaScript(releaseAgreementCheckbox);
        JDIPageUtils.verifyCheckBoxClicked(releaseAgreementCheckbox);
        shortWait.until(ExpectedConditions.elementSelectionStateToBe(releaseAgreementCheckbox, true));
        logger.info("User has selected the release agreement in medical release.");
    }

    public void clickSubmit() {
        shortWait.until(ExpectedConditions.visibilityOf(submit));
        JDIPageUtils.scrollDownToElement(SUBMIT_BUTTON_XPATH);
        submit.click();
        //JDIPageUtils.doubleClickAndWait(submit);
    }

    public void waitUntilContentDisplayed() {
        //Keep the below wait so that there is some delay to use the scrollToTopOfPage() method
        shortWait.until(ExpectedConditions.visibilityOf(medicalReleaseFormContent));

        //Only automated tests do not load page at top, on browser page is fine
        //Keep below so the page test is started from the top
        JDIPageUtils.scrollToTopOfPage();

        shortWait.until(ExpectedConditions.visibilityOf(helpInformationSubtitle));
        Assert.assertTrue(helpInformationSubtitle.isDisplayed());

        shortWait.until(ExpectedConditions.visibilityOf(medicalReleaseFormContent));
        Assert.assertTrue(medicalReleaseFormContent.isDisplayed());
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
        waitUntilContentDisplayed();
    }
}
