package org.broadinstitute.ddp.pages.brainpages;

import java.util.List;

import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(DashboardPage.class);

    private static final String DASHBOARD_XPATH = "//ddp-dashboard";
    private static final String DASHBOARD_ANNOUNCEMENT_MESSAGE_XPATH = "//section[contains(@class, 'Dashboard-info-section')]";
    private static final String DASHBOARD_ANNOUNCEMENT_CLOSE_ICON_XPATH = "//mat-icon[contains(@class, 'close')]";
    private static final String DASHBOARD_ACTIVITIES_XPATH = "//*[@data-ddp-test='activitiesTable']//mat-row";
    private static final String DASHBOARD_ACTIVITIES_NAME_XPATH = "//button[contains(@data-ddp-test, 'activityName')]";
    private static final String DASHBOARD_ACTIVITIES_STATUS_XPATH = "//mat-cell[contains(@data-ddp-test, 'activityStatus')]";

    @FindBy(xpath = DASHBOARD_XPATH)
    private WebElement dashboard;

    @FindBy(xpath = DASHBOARD_ANNOUNCEMENT_MESSAGE_XPATH)
    private WebElement announcementMessage;

    @FindBy(xpath = DASHBOARD_ANNOUNCEMENT_CLOSE_ICON_XPATH)
    private WebElement announcementCloseIcon;

    @FindBy(xpath = DASHBOARD_ACTIVITIES_XPATH)
    private List<WebElement> dashboardActivities;

    public void verifyDashboardDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(dashboard));
        Assert.assertTrue(dashboard.isDisplayed());
    }

    public void listAllActivitiesWithStatuses() {
        logger.info("There are {} activities for this user", dashboardActivities.size());

        for (WebElement activity : dashboardActivities) {
            WebElement activityName = activity.findElement(By.xpath(DASHBOARD_ACTIVITIES_NAME_XPATH));
            WebElement activityStatus = activity.findElement(By.xpath(DASHBOARD_ACTIVITIES_STATUS_XPATH));
            String name = JDIPageUtils.getWebElementText(activityName);
            String status = JDIPageUtils.getWebElementText(activityStatus);

            logger.info("ACTIVITY [{}] is {}", name, status);
        }

    }

    public void verifyDashboardAnnouncementMessageDisplayed() {
        JDIPageUtils.scrollToTopOfPage();
        shortWait.until(ExpectedConditions.visibilityOf(announcementMessage));
        Assert.assertTrue(announcementMessage.isDisplayed());
    }

    public void closeAnnoucementMessage() {
        shortWait.until(ExpectedConditions.visibilityOf(announcementCloseIcon));
        announcementCloseIcon.click();
        verifyDashboardAnnouncementMessageNotDisplayed();
    }

    private void verifyDashboardAnnouncementMessageNotDisplayed() {
        Assert.assertFalse(announcementMessage.isDisplayed());
    }

    public void waitUntilDashboardContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(dashboard));
        for (WebElement activity : dashboardActivities) {
            shortWait.until(ExpectedConditions.visibilityOf(activity));
        }
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
    }
}
