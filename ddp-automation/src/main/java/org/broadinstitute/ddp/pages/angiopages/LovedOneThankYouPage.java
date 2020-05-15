package org.broadinstitute.ddp.pages.angiopages;

import org.broadinstitute.ddp.pages.DDPPage;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LovedOneThankYouPage extends DDPPage {

    private static final Logger logger = LoggerFactory.getLogger(LovedOneThankYouPage.class);

    private static final String THANK_YOU_HEADER_XPATH = "//h1[contains(text(), 'Thank you!')]";
    private static final String THANK_YOU_MESSAGE_XPATH = "//section";

    @FindBy(xpath = THANK_YOU_HEADER_XPATH)
    private WebElement header;

    @FindBy(xpath = THANK_YOU_MESSAGE_XPATH)
    private WebElement thankYouMessage;

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
    }

    public void verifyHeaderDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(header));
        Assert.assertTrue(header.isDisplayed());
    }

    public void verifyThankYouMessageDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(thankYouMessage));
        Assert.assertTrue(thankYouMessage.isDisplayed());
    }
}
