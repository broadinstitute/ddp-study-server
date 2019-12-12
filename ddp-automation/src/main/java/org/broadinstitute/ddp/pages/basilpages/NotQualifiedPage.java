package org.broadinstitute.ddp.pages.basilpages;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByTag;
import org.broadinstitute.ddp.pages.DDPPage;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotQualifiedPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(NotQualifiedPage.class);

    private static final String CONTENT_TAG = "app-not-qualified";

    @ByTag(CONTENT_TAG)
    private WebElement notQualifiedContent;

    public void verifyNotQualifiedContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(notQualifiedContent));
        Assert.assertTrue(notQualifiedContent.isDisplayed());
    }

    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        verifyNotQualifiedContentDisplayed();
    }
}
