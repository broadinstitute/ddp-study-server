package org.broadinstitute.ddp.pages.basilpages;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByTag;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByText;
import org.broadinstitute.ddp.pages.DDPPage;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(DashboardPage.class);

    private static final String CONTENT_TAG = "app-dashboard";
    private static final String BASIL_STUDY_NAME = "Join the Basil Research Study!";


    @ByText(BASIL_STUDY_NAME)
    private WebElement basilStudy;

    @ByTag(CONTENT_TAG)
    private WebElement dashboardContent;

    public void verifyDashboardContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(dashboardContent));
        Assert.assertTrue(dashboardContent.isDisplayed());
    }

    public void clickBasilStudy() {
        shortWait.until(ExpectedConditions.visibilityOf(basilStudy));
        basilStudy.click();
    }

    /**
     * Waits until url is reached (and previous url is finished) before checking.
     * urlTemplate is set at BasilAppSite.java for each page object
     */
    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        verifyDashboardContentDisplayed();
    }
}
