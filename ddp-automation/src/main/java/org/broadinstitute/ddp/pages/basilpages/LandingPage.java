package org.broadinstitute.ddp.pages.basilpages;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByText;
import org.broadinstitute.ddp.pages.DDPPage;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LandingPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(LandingPage.class);
    private static final String THANKS_MESSAGE = "Thank you!";

    @ByText(THANKS_MESSAGE)
    private WebElement thanksMessage;

    public void assertThanksMessageIsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(thanksMessage));
        Assert.assertTrue(thanksMessage.isDisplayed());
    }

    /**
     * Waits until url is reached (and previous url is finished) before checking.
     * urlTemplate is set at BasilAppSite.java for each page object
     */
    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
    }
}
