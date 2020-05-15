package org.broadinstitute.ddp.pages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.composite.WebPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
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
    private static final String USER_MENU_ICON = "//mat-toolbar//ddp-user-menu//button";
    private static final String USER_MENU_DASHBOARD_OPTION = "//button[@role='menuitem']//span[contains(text(), 'Dashboard')]";
    private static final String USER_MENU_SIGN_OUT_OPTION = "//button[@role='menuitem']//span[contains(text(), 'Sign Out')]";
    protected static final String CHECKTYPE_EQUAL = "EQUAL";
    protected static final String CHECKTYPE_CONTAINS = "CONTAINS";
    protected static final String CHECKTYPE_MATCH = "MATCH";
    private static final String CHECKTYPE_MATCH_REGEX = "^[A-Za-z0-9\\d_-]";

    //Question numbers, used to get which one to answer
    public static final String QUESTION_ONE = "1";
    public static final String QUESTION_TWO = "2";
    public static final String QUESTION_THREE = "3";
    public static final String QUESTION_FOUR = "4";
    public static final String QUESTION_FIVE = "5";
    public static final String QUESTION_SIX = "6";
    public static final String QUESTION_SEVEN = "7";
    public static final String QUESTION_EIGHT = "8";
    public static final String QUESTION_NINE = "9";
    public static final String QUESTION_TEN = "10";
    public static final String QUESTION_ELEVEN = "11";
    public static final String QUESTION_TWELVE = "12";
    public static final String QUESTION_THIRTEEN = "13";
    public static final String QUESTION_FOURTEEN = "14";
    public static final String QUESTION_FIFTEEN = "15";
    public static final String QUESTION_SIXTEEN = "16";
    public static final String QUESTION_SEVENTEEN = "17";
    public static final String CONDITIONAL_SPECIFIER_FIRST = "[1]";
    public static final String CONDITIONAL_SPECIFIER_SECOND = "[2]";
    public static final String CONDITIONAL_SPECIFIER_THIRD = "[3]";
    public static final String CONDITIONAL_SPECIFIER_FOURTH = "[4]";

    //shortWait was previously set to 90
    protected WebDriverWait shortWait = new WebDriverWait(getDriver(), 60);
    protected WebDriverWait longWait = new WebDriverWait(getDriver(), 120);
    private boolean isAMobilePage = false;

    @FindBy(xpath = GENERAL_PAGE_CONTENT_XPATH)
    private WebElement generalContent;

    @FindBy(xpath = USER_MENU_ICON)
    private WebElement userMenu;

    @FindBy(xpath = USER_MENU_DASHBOARD_OPTION)
    private WebElement userMenuDashboardOption;

    @FindBy(xpath = USER_MENU_SIGN_OUT_OPTION)
    private WebElement usereMenuSignOutOption;

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


    public WebElement getWebElementUsingXPath(String xpathSelector) {
        return this.getDriver().findElement(By.xpath(xpathSelector));
    }

    /**
     * Deals with naviagating to dashboard using the header in angio
     */
    public void clickUserMenuOption() {
        shortWait.until(ExpectedConditions.visibilityOf(userMenu));
        JDIPageUtils.clickButtonUsingJDI(USER_MENU_ICON, XPATH);
    }

    public void clickDashboardOption() {
        shortWait.until(ExpectedConditions.visibilityOf(userMenuDashboardOption));
        JDIPageUtils.clickButtonUsingJDI(USER_MENU_DASHBOARD_OPTION, XPATH);
    }

    public void clickSignOutOption() {
        shortWait.until(ExpectedConditions.visibilityOf(usereMenuSignOutOption));
        JDIPageUtils.clickButtonUsingJDI(USER_MENU_SIGN_OUT_OPTION, XPATH);
    }

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
