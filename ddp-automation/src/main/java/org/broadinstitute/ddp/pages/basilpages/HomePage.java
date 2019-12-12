package org.broadinstitute.ddp.pages.basilpages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.Attribute;
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

    private static final String LOGIN_BUTTON_TEXT = "GET STARTED!";
    private static final String LOGOUT_BUTTON_ATTRIBUTE_VALUE = "logoutButton";
    private static final String APP_LOGOUT_BUTTON_RELATIVE_PATH = "//app-welcome//ddp-login"
            + "//button[@data-ddp-test='logoutButton']";
    private static final String USER_PROFILE_ATTRIBUTE_VALUE = "profileButton";
    private static final String MOBILE_MENU_ELEMENT_CONTENT = "menu";
    private static final String HOMEPAGE_CONTENT_XPATH = "//div[@class='PageContent']";

    @ByText(LOGIN_BUTTON_TEXT)
    private Button login;

    @FindBy(xpath = APP_LOGOUT_BUTTON_RELATIVE_PATH)
    private Button logout;

    @ByText(MOBILE_MENU_ELEMENT_CONTENT)
    private Button mobileMenu;

    @Attribute(name = STABLE_ID_IDENTIFIER, value = USER_PROFILE_ATTRIBUTE_VALUE)
    private Button userProfileButton;

    @FindBy(xpath = HOMEPAGE_CONTENT_XPATH)
    private WebElement homepageContent;

    public void clickLogin() {
        shortWait.until(ExpectedConditions.visibilityOf(login.getWebElement()));
        login.click();
    }

    public void clickLogout() {
        if (hasMobileMenu()) {
            mobileMenu.click();
        }
        clickBasilHeaderLogout();
    }

    private boolean hasMobileMenu() {
        return mobileMenu.isDisplayed();
    }

    public void clickUserProfile() {
        shortWait.until(ExpectedConditions.visibilityOf(userProfileButton.getWebElement()));
        userProfileButton.click();
    }

    private void waitUntilHomapgeContentDisplayed() {
        shortWait.until(ExpectedConditions.invisibilityOf(homepageContent));
        Assert.assertTrue(homepageContent.isDisplayed());
    }

    /**
     * Waits until url is reached (and previous url is finished) before checking.
     * urlTemplate is set at BasilAppSite.java for each page object
     */
    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
        waitUntilHomapgeContentDisplayed();
    }
}
