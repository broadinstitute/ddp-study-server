package org.broadinstitute.ddp.pages.sandboxpages;

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

public class ActivityInstancePage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(ActivityInstancePage.class);

    private static final String ACTIVITY_INSTANCE_PAGE_TAG = "app-sandbox-activity";
    private static final String STEPPER_XPATH = "//*[@data-ddp-test='isStepper']//input";
    private static final String STEP_TAG = "ddp-step-activity";
    private static final String STUDY_GUID_XPATH = "//*[@data-ddp-test='prequalifierStudyGuid']//input";
    private static final String ACTIVITY_GUID_XPATH = "//*[@data-ddp-test='prequalifierActivityGuid']//input";
    private static final String TOTAL_SUBMISSION_COUNT_XPATH = "//*[@data-ddp-test='totalCount']";
    private static final String LOADING_PROGRESS_BAR_XPATH = "//*[@data-ddp-test='loadingProgress']";
    private static final String ACTIVITY_FORM_TAG = "ddp-activity";
    private static final String SUBMIT_BUTTON_TEXT = "SUBMIT";

    @ByTag(ACTIVITY_INSTANCE_PAGE_TAG)
    private WebElement activityInstanceElements;

    @FindBy(xpath = STEPPER_XPATH)
    private WebElement stepperToggle;

    @ByTag(STEP_TAG)
    private List<WebElement> stepTags;

    @FindBy(xpath = STUDY_GUID_XPATH)
    private WebElement studyGuid;

    @FindBy(xpath = ACTIVITY_GUID_XPATH)
    private WebElement activityGuid;

    @FindBy(xpath = TOTAL_SUBMISSION_COUNT_XPATH)
    private WebElement totalCountOfActivitySubmission;

    @FindBy(xpath = LOADING_PROGRESS_BAR_XPATH)
    private WebElement progressBar;

    @ByTag(ACTIVITY_FORM_TAG)
    private WebElement activityForm;

    @ByText(SUBMIT_BUTTON_TEXT)
    private Button submit;

    public void verifyStudyGuidDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(studyGuid));
        Assert.assertTrue(studyGuid.isDisplayed());
    }

    public void verifyActivityGuidDisplayed(String guid) {
        logger.info("Checking that correct guid is displayed...");
        //Check if guid is displayd
        shortWait.until(ExpectedConditions.visibilityOf(activityGuid));
        Assert.assertTrue(activityGuid.isDisplayed());

        //Check if correct guid is displayed
        String textfieldGuid = JDIPageUtils.getWebElementText(activityGuid);
        Assert.assertTrue(textfieldGuid.equals(guid));
    }

    public void inputActivityGuid(String guid) {
        shortWait.until(ExpectedConditions.visibilityOf(activityGuid));
        logger.info("Inputting activity GUID {}", guid);
        JDIPageUtils.inputText(activityGuid, guid);
        waitUntilActivityDisplayed();
    }

    public void waitUntilActivityDisplayed() {
        shortWait.until(ExpectedConditions.invisibilityOf(progressBar));
        shortWait.until(ExpectedConditions.visibilityOf(activityForm));
    }

    public void verifyActivityDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(activityForm));
        Assert.assertTrue(activityForm.isDisplayed());
    }

    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
    }
}
