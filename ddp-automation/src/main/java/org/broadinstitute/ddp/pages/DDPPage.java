package org.broadinstitute.ddp.pages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.composite.WebPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DDPPage extends WebPage {
    private static final Logger logger = LoggerFactory.getLogger(DDPPage.class);

    protected static final String STABLE_ID_IDENTIFIER = "data-ddp-test";
    protected static final String XPATH = "xpath";
    protected static final String CSS = "css";
    protected static final String YES = "Yes";
    protected static final String NO = "No";
    protected static final String I_DONT_KNOW = "I don\'t know";
    protected static final String MONTH = "Month";
    protected static final String DAY = "Day";
    protected static final String YEAR = "Year";
    private static final String GENERAL_PAGE_CONTENT_XPATH = "//app-root";
    private static final String LOGOUT_BUTTON_RELATIVE_PATH = "//mat-toolbar//button[@data-ddp-test='logoutButton']";
    private static final String LOGIN_BUTTON_RELATIVE_PATH = "//mat-toolbar//button[@data-ddp-test='loginButton']";
    private static final String MOBILE_MENU_TEXT = "menu";
    private static final String MOBILE_MENU_BUTTON = "//span[@class='MenuButton']";
    private static final String MOBILE_MENU_ELEMENT_XPATH = "//button[@aria-haspopup='true']";
    private static final String MOBILE_MENU_LOGOUT_XPATH = "//body//a//ddp-login//button[@data-ddp-test='logoutButton']";
    private static final String LOGO_XPATH = "//mat-toolbar//div[contains(@class, 'Header-logoText')]";
    private static final String HEADER_XPATH = "//mat-toolbar";
    protected static final String CHECKTYPE_EQUAL = "EQUAL";
    protected static final String CHECKTYPE_CONTAINS = "CONTAINS";
    protected static final String CHECKTYPE_MATCH = "MATCH";
    private static final String CHECKTYPE_MATCH_REGEX = "^[A-Za-z0-9\\d_-]";

    //shortWait was previously set to 90
    protected WebDriverWait shortWait = new WebDriverWait(getDriver(), 60);
    protected WebDriverWait longWait = new WebDriverWait(getDriver(), 120);
    private boolean isAMobilePage = false;

    @FindBy(xpath = GENERAL_PAGE_CONTENT_XPATH)
    private WebElement generalContent;

    @FindBy(xpath = HEADER_XPATH)
    private WebElement basilHeader;

    @FindBy(xpath = LOGO_XPATH)
    private WebElement basilLogo;

    @FindBy(xpath = LOGOUT_BUTTON_RELATIVE_PATH)
    private Button basilHeaderLogout;

    @FindBy(xpath = LOGIN_BUTTON_RELATIVE_PATH)
    private Button basilHeaderLogin;

    @FindBy(xpath = MOBILE_MENU_ELEMENT_XPATH)
    private Button basilMobileMenu;

    @FindBy(xpath = MOBILE_MENU_LOGOUT_XPATH)
    private Button basilMobileLogoutButton;

    /**
     * Click the logout button in the header/menu
     */
    public void clickBasilHeaderLogout() {
        if (hasMobileMenu()) {
            clickBasilMobileLogout();
        } else {
            shortWait.until(ExpectedConditions.visibilityOf(basilHeaderLogout.getWebElement()));
            logger.info("[DDPPage Logout] Current URL: {}", getDriver().getCurrentUrl());
            basilHeaderLogout.click();
        }
    }

    /**
     * Click the login button in the header/menu
     */
    public void clickBasilHeaderLogin() {
        shortWait.until(ExpectedConditions.visibilityOf(basilHeaderLogin.getWebElement()));
        basilHeaderLogin.click();
    }

    private void clickBasilMobileLogout() {
        JDIPageUtils.scrollToTopOfPage();
        basilMobileMenu.click();
        shortWait.until(ExpectedConditions.visibilityOf(basilMobileLogoutButton.getWebElement()));
        basilMobileLogoutButton.click();
    }

    public boolean isMobilePage() {
        return isAMobilePage;
    }


    private boolean hasMobileMenu() {
        return basilMobileMenu.isDisplayed();
    }

    /*
     * Used to renew the shortwait. Without this shortwait will throw NoSuchSessionException: session id is null
     * if trying to run sequential tests after the first test run. Even if the driver's session id
     * is not null, the wait's session id will be null.
     */
    private void renewShortWait() {
        shortWait = new WebDriverWait(getDriver(), 90);
    }

    /**
     * Personal fix for checking and verifying that the URL has been reached since JDI's doesn't seem to work
     * @param urlTemplate the section of the url to look for e.g. '/prequalifier' (no quotes)
     * @param urlCheckType what sort of check to do with the url and urlTemplate i.e. EQUAL, CONTAINS, and MATCH
     */
    public void verifyOpened(String urlTemplate, String urlCheckType) {
        String currentUrl = null;
        renewShortWait();

        if (urlCheckType.equals(CHECKTYPE_EQUAL)) {
            logger.info("Checking if URL equals: {}", urlTemplate);
            shortWait.until(ExpectedConditions.urlToBe(urlTemplate));
            currentUrl = getDriver().getCurrentUrl();
            Assert.assertTrue(currentUrl.equals(urlTemplate));

        } else if (urlCheckType.equals(CHECKTYPE_CONTAINS)) {
            logger.info("Checking if URL contains: {}", urlTemplate);
            shortWait.until(ExpectedConditions.urlContains(urlTemplate));
            currentUrl = getDriver().getCurrentUrl();
            Assert.assertTrue(currentUrl.contains(urlTemplate));

        } else if (urlCheckType.equals(CHECKTYPE_MATCH)) {
            logger.info("Checking if URL has a [regex] match for: {}", urlTemplate);
            shortWait.until(ExpectedConditions.urlMatches(CHECKTYPE_MATCH_REGEX));
            currentUrl = getDriver().getCurrentUrl();
            Assert.assertTrue(currentUrl.equalsIgnoreCase(urlTemplate));

        }
        logger.info("Current URL: {}", currentUrl);
    }
}
