package org.broadinstitute.ddp.pages.basilpages;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByTag;
import org.broadinstitute.ddp.pages.DDPPage;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeclinedConsentPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(DeclinedConsentPage.class);

    private static final String CONTENT_TAG = "app-consent-declined";

    @ByTag(CONTENT_TAG)
    private WebElement declinedConsentContent;

    public void verifyDeclinedConsentContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(declinedConsentContent));
        Assert.assertTrue(declinedConsentContent.isDisplayed());
    }

    /**
     * Waits until url is reached (and previous url is finished) before checking.
     * urlTemplate is set at BasilAppSite.java for each page object
     */
    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        verifyDeclinedConsentContentDisplayed();
    }
}
