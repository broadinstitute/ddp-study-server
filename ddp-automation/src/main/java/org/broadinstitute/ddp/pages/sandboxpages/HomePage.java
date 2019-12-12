package org.broadinstitute.ddp.pages.sandboxpages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.common.Link;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByTag;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByText;
import org.broadinstitute.ddp.pages.DDPPage;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HomePage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(HomePage.class);

    private static final String BACKEND_REQUEST_SECTION_TEXT = "Backend requests";
    private static final String ADD_NETWORK_SNIFFER_XPATH = "//*[@data-ddp-test='addSnifferUrlButton']";
    private static final String NEW_REQUEST_MOCK_TAG = "ddp-new-request-mock";
    private static final String MOCK_REQUEST_SAVE_TEXT = "SAVE";
    private static final String MOCK_REQUEST_CANCEL_TEXT = "CANCEL";
    private static final String USER_AUTHENTICATION_SECTION_TEXT = "User & Authentication";
    private static final String LOGIN_LOGOUT_SECTION_XPATH = "//a[@routerlink='/login']";
    private static final String USER_PROFILE_SECTION_XPATH = "//a[@routerlink='/userprofile']";
    private static final String PARTICIPANT_PROFILE_SECTION_XPATH = "//a[@routerlink='/participantprofile']";
    private static final String ACTIVITIES_SECTION_TEXT = "Activitites";
    private static final String ACTIVITIES_LIST_SECTION_XPATH = "//a[@routerlink='/activitiesList']";
    private static final String ACTIVITY_INSTANCE_SECTION_XPATH = "//a[@routerlink='/activity']";
    private static final String READONLY_ACTIVITY_FORM_SECTION_XPATH = "//a[@routerlink='/readonly-activity-form']";
    private static final String ACTIVITY_FORM_SECTION_TEXT = "Activity form";
    private static final String BOOLEAN_QUESTION_SECTION_XPATH = "//a[@routerlink='/boolean-question']";
    private static final String TEXT_QUESTION_SECTION_XPATH = "//a[@routerlink='/text-question']";
    private static final String DATE_PICKER_QUESTION_SECTION_XPATH = "//a[@routerlink='/date-picker-question']";
    private static final String PICKLIST_QUESTION_SECTION_XPATH = "//a[@routerlink='/picklist-question']";
    private static final String MISC_SECTION_TEXT = "Misc";
    private static final String FIRECLOUD_STUDIES_SECTION_XPATH = "//a[@routerlink='/fireCloudStudies']";

    @ByText(BACKEND_REQUEST_SECTION_TEXT)
    private Button backendRequest;

    @FindBy(xpath = ADD_NETWORK_SNIFFER_XPATH)
    private Button addNetworkSniffer;

    @ByTag(NEW_REQUEST_MOCK_TAG)
    private WebElement newRequestMock;

    @ByText(MOCK_REQUEST_SAVE_TEXT)
    private Button mockSave;

    @ByText(MOCK_REQUEST_CANCEL_TEXT)
    private Button mockCancel;

    @ByText(USER_AUTHENTICATION_SECTION_TEXT)
    private Button userAuthentication;

    @FindBy(xpath = LOGIN_LOGOUT_SECTION_XPATH)
    private Link loginLogout;

    @FindBy(xpath = USER_PROFILE_SECTION_XPATH)
    private Link userProfile;

    @FindBy(xpath = PARTICIPANT_PROFILE_SECTION_XPATH)
    private Link participantProfile;

    @ByText(ACTIVITIES_SECTION_TEXT)
    private Button activities;

    @FindBy(xpath = ACTIVITIES_LIST_SECTION_XPATH)
    private Link activityList;

    @FindBy(xpath = ACTIVITY_INSTANCE_SECTION_XPATH)
    private Link activityInstance;

    @FindBy(xpath = READONLY_ACTIVITY_FORM_SECTION_XPATH)
    private Link readonlyActivity;

    @ByText(ACTIVITY_FORM_SECTION_TEXT)
    private Button activityForm;

    @FindBy(xpath = BOOLEAN_QUESTION_SECTION_XPATH)
    private Link booleanQuestion;

    @FindBy(xpath = TEXT_QUESTION_SECTION_XPATH)
    private Link textQuestion;

    @FindBy(xpath = DATE_PICKER_QUESTION_SECTION_XPATH)
    private Link datePickerQuestion;

    @FindBy(xpath = PICKLIST_QUESTION_SECTION_XPATH)
    private Link picklistQuestion;

    @ByText(MISC_SECTION_TEXT)
    private Button miscellaneous;

    @FindBy(xpath = FIRECLOUD_STUDIES_SECTION_XPATH)
    private Link firecloudStudies;

    public void clickBackendRequest() {
        shortWait.until(ExpectedConditions.visibilityOf(backendRequest.getWebElement()));
        backendRequest.click();
    }

    public void clickAddNetworkSniffer() {
        shortWait.until(ExpectedConditions.visibilityOf(addNetworkSniffer.getWebElement()));
        addNetworkSniffer.click();
    }

    public void verifyNewRequestMockDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(newRequestMock));
        Assert.assertTrue(newRequestMock.isDisplayed());
    }

    public void clickMockSave() {
        shortWait.until(ExpectedConditions.visibilityOf(mockSave.getWebElement()));
        mockSave.click();
    }

    public void clickMockCancel() {
        shortWait.until(ExpectedConditions.visibilityOf(mockCancel.getWebElement()));
        mockCancel.click();
    }

    public void clickUserAndAuthenticationSection() {
        shortWait.until(ExpectedConditions.visibilityOf(userAuthentication.getWebElement()));
        userAuthentication.click();
    }

    public void clickLoginAndLogoutPage() {
        shortWait.until(ExpectedConditions.visibilityOf(loginLogout.getWebElement()));
        loginLogout.click();
    }

    public void clickUserProfilePage() {
        shortWait.until(ExpectedConditions.visibilityOf(userProfile.getWebElement()));
        userProfile.click();
    }

    public void clickParticipantProfilePage() {
        shortWait.until(ExpectedConditions.visibilityOf(participantProfile.getWebElement()));
        participantProfile.click();
    }

    public void verifyUserAndAuthenticationWebElementsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOfAllElements(loginLogout.getWebElement(),
                userProfile.getWebElement(),
                participantProfile.getWebElement()));

        Assert.assertTrue(loginLogout.isDisplayed());
        Assert.assertTrue(userProfile.isDisplayed());
        Assert.assertTrue(participantProfile.isDisplayed());
    }

    public void clickActivitiesSection() {
        shortWait.until(ExpectedConditions.visibilityOf(activities.getWebElement()));
        activities.click();
    }

    public void clickActivitiesListPage() {
        shortWait.until(ExpectedConditions.visibilityOf(activityList.getWebElement()));
        activityList.click();
    }

    public void clickActivityInstancePage() {
        shortWait.until(ExpectedConditions.visibilityOf(activityInstance.getWebElement()));
        activityInstance.click();
    }

    public void clickReadonlyActivityFormPage() {
        shortWait.until(ExpectedConditions.visibilityOf(readonlyActivity.getWebElement()));
        readonlyActivity.click();
    }

    public void verifyActivitiesWebElementsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOfAllElements(activityList.getWebElement(),
                activityInstance.getWebElement(),
                readonlyActivity.getWebElement()));

        Assert.assertTrue(activityList.isDisplayed());
        Assert.assertTrue(activityInstance.isDisplayed());
        Assert.assertTrue(readonlyActivity.isDisplayed());
    }

    public void clickActivityFormSection() {
        shortWait.until(ExpectedConditions.visibilityOf(activityForm.getWebElement()));
        activityForm.click();
    }

    public void clickBooleanQuestionPage() {
        shortWait.until(ExpectedConditions.visibilityOf(booleanQuestion.getWebElement()));
        booleanQuestion.click();
    }

    public void clickTextQuestionPage() {
        shortWait.until(ExpectedConditions.visibilityOf(textQuestion.getWebElement()));
        textQuestion.click();
    }

    public void clickDatePickerQuestionPage() {
        shortWait.until(ExpectedConditions.visibilityOf(datePickerQuestion.getWebElement()));
        datePickerQuestion.click();
    }

    public void clickPicklistQuestionPage() {
        shortWait.until(ExpectedConditions.visibilityOf(picklistQuestion.getWebElement()));
        picklistQuestion.click();
    }

    public void verifyActivityFormWebElementsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOfAllElements(booleanQuestion.getWebElement(),
                textQuestion.getWebElement(),
                datePickerQuestion.getWebElement(),
                picklistQuestion.getWebElement()));

        Assert.assertTrue(booleanQuestion.isDisplayed());
        Assert.assertTrue(textQuestion.isDisplayed());
        Assert.assertTrue(datePickerQuestion.isDisplayed());
        Assert.assertTrue(picklistQuestion.isDisplayed());
    }

    public void clickMiscSection() {
        shortWait.until(ExpectedConditions.visibilityOf(miscellaneous.getWebElement()));
        miscellaneous.click();
    }

    public void clickFirecloudStudiesPage() {
        shortWait.until(ExpectedConditions.visibilityOf(firecloudStudies.getWebElement()));
        firecloudStudies.click();
    }

    public void verifyMiscWebElementsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOfAllElements(firecloudStudies.getWebElement()));

        Assert.assertTrue(firecloudStudies.isDisplayed());
    }

    public void waitUntilHomePageWebElementsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(backendRequest.getWebElement()));
        shortWait.until(ExpectedConditions.visibilityOf(userAuthentication.getWebElement()));
        shortWait.until(ExpectedConditions.visibilityOf(activities.getWebElement()));
        shortWait.until(ExpectedConditions.visibilityOf(activityForm.getWebElement()));
        shortWait.until(ExpectedConditions.visibilityOf(miscellaneous.getWebElement()));
    }

    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        waitUntilHomePageWebElementsDisplayed();
    }
}
