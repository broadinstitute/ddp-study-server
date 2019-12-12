package org.broadinstitute.ddp.pages.basilpages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.Attribute;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByTag;
import org.broadinstitute.ddp.pages.DDPPage;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WelcomePage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(WelcomePage.class);
    private static final String LOGIN_BUTTON_ATTRIBUTE_VALUE = "loginButton";
    private static final String WELCOME_PAGE_CONTENT_TAG = "app-welcome";

    @Attribute(name = STABLE_ID_IDENTIFIER, value = LOGIN_BUTTON_ATTRIBUTE_VALUE)
    private Button login;

    @ByTag(WELCOME_PAGE_CONTENT_TAG)
    private WebElement welcomeContent;

    public void clickLogin() {
        shortWait.until(ExpectedConditions.visibilityOf(login.getWebElement()));
        login.click();
    }

    public void verifyWelcomeContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(welcomeContent));
        Assert.assertTrue(welcomeContent.isDisplayed());
    }

    /**
     * Waits until url is reached (and previous url is finished) before checking.
     * urlTemplate is set at BasilAppSite.java for each page object
     */
    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        verifyWelcomeContentDisplayed();
    }
}
