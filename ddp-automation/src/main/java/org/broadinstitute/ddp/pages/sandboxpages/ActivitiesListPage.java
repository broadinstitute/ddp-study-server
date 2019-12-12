package org.broadinstitute.ddp.pages.sandboxpages;

import java.util.List;

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

public class ActivitiesListPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(ActivitiesListPage.class);

    private static final String ACTIVITIES_LIST_TAG = "app-sandbox-activities-list";
    private static final String STUDY_GUID_XPATH = "//*[@data-ddp-test='activitiesStudyGuid']//input";
    private static final String TOTAL_GUID_REQUESTS_XPATH = "//*[@data-ddp-test='totalCount']";
    private static final String SELECTED_ACTIVITY_GUID_XPATH = "//*[@data-ddp-test='selectedActivity']";
    private static final String ACTIVITIY_DASHBOARD_XPATH = "//*[@data-ddp-test='activitiesTable']";
    private static final String BASIL_STUDY_NAME = "Join the Basil Research Study!";
    private static final String CONDITIONAL_FORM_ACTIVITY_NAME = "Conditional Form for Testing Content Question Toggle";
    private static final String ACTIVITY_STATUS_XPATH = "//*[contains(@data-ddp-test, 'activityStatus')]";

    @ByTag(ACTIVITIES_LIST_TAG)
    private WebElement activitiesList;

    @FindBy(xpath = STUDY_GUID_XPATH)
    private WebElement studyGuidTextField;

    @FindBy(xpath = TOTAL_GUID_REQUESTS_XPATH)
    private WebElement totalGuidRequests;

    @FindBy(xpath = SELECTED_ACTIVITY_GUID_XPATH)
    private WebElement selectedGuid;

    @FindBy(xpath = ACTIVITIY_DASHBOARD_XPATH)
    private WebElement activityDashboard;

    @ByText(BASIL_STUDY_NAME)
    private WebElement basilStudy;

    @ByText(CONDITIONAL_FORM_ACTIVITY_NAME)
    private List<WebElement> conditionalForms;

    private String activityGuid;

    public void verifyActivityListWebElementsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(activitiesList));
        Assert.assertTrue(activitiesList.isDisplayed());
    }

    public void verifyStudyGuidDisplayed(String studyGuid) {
        shortWait.until(ExpectedConditions.visibilityOf(studyGuidTextField));
        String text = JDIPageUtils.getWebElementText(studyGuidTextField);
        Assert.assertTrue(text.equals(studyGuid));
    }

    public void verifyBasilStudyDisplayed(String studyName) {
        shortWait.until(ExpectedConditions.visibilityOf(basilStudy));
        String text = JDIPageUtils.getWebElementText(basilStudy);
        Assert.assertTrue(text.equals(studyName));
    }

    public void clickBasilStudy() {
        shortWait.until(ExpectedConditions.visibilityOf(basilStudy));
        basilStudy.click();
    }

    public void verifyActivityInstanceGuidDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(selectedGuid));
        Assert.assertTrue(selectedGuid.isDisplayed());
    }

    public void copyActivityInstanceGuid() {
        shortWait.until(ExpectedConditions.visibilityOf(selectedGuid));
        setActivityGuid();
    }

    private void setActivityGuid() {
        logger.info("Copying the activity GUID...");
        activityGuid = JDIPageUtils.getWebElementText(selectedGuid);
    }

    public String getActivityGuid() {
        return activityGuid;
    }

    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        verifyActivityListWebElementsDisplayed();
    }
}
