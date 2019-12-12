package org.broadinstitute.ddp;

import static com.epam.jdi.uitests.core.interfaces.complex.tables.interfaces.CheckPageTypes.CONTAINS;
import static com.epam.jdi.uitests.web.settings.WebSettings.getDriver;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.JPage;
import org.broadinstitute.ddp.pages.Auth0Page;
import org.broadinstitute.ddp.pages.basilpages.PrequalifierPage;
import org.broadinstitute.ddp.pages.sandboxpages.ActivitiesListPage;
import org.broadinstitute.ddp.pages.sandboxpages.ActivityInstancePage;
import org.broadinstitute.ddp.pages.sandboxpages.DatesPage;
import org.broadinstitute.ddp.pages.sandboxpages.HomePage;
import org.broadinstitute.ddp.pages.sandboxpages.LoginPage;
import org.broadinstitute.ddp.pages.sandboxpages.ParticipantProfilePage;
import org.broadinstitute.ddp.pages.sandboxpages.UserProfilePage;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SandboxAppSite extends SandboxAppWebsite {
    private static final Logger logger = LoggerFactory.getLogger(SandboxAppSite.class);

    private static final String MOBILE_MENU_ELEMENT_CONTENT = "//mat-toolbar//button//mat-icon[text()='menu']";
    private static final String LOGIN_BUTTON_XPATH = "//mat-toolbar//ddp-login"
            + "//button[@data-ddp-test='loginButton']";
    private static final String LOGOUT_BUTTON_XPATH = "//mat-toolbar//ddp-login"
            + "//button[@data-ddp-test='logoutButton']";
    private static final String MOBILE_MENU_POPUP_XPATH = "//mat-sidenav";
    private static final String AUTH0PAGE_PAGE_URL_TEMPLATE = "auth0.com";
    private static final String HOME_PAGE_URL_TEMPLATE = "/";
    private static final String LOGIN_PAGE_URL_TEMPLATE = "/login";
    private static final String USER_PROFILE_PAGE_URL_TEMPLATE = "/userprofile";
    private static final String PARTICIPANT_PROFILE_PAGE_URL_TEMPLATE = "participantprofile";
    private static final String ACTIVITIES_LIST_PAGE_URL_TEMPLATE = "/activitiesList";
    private static final String ACTIVITY_INSTANCE_PAGE_URL_TEMPLATE = "/activity";
    private static final String DATE_PAGE_URL_TEMPLATE = "/date";

    private static WebDriverWait shortWait = new WebDriverWait(getDriver(), 30);
    private static WebDriverWait longWait = new WebDriverWait(getDriver(), 60);

    @FindBy(xpath = LOGIN_BUTTON_XPATH)
    private static Button headerLogin;

    @FindBy(xpath = LOGOUT_BUTTON_XPATH)
    private static Button headerLogout;

    @JPage(urlTemplate = HOME_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static HomePage homePage;

    @JPage(urlTemplate = AUTH0PAGE_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static Auth0Page auth0Page;

    @JPage(urlTemplate = LOGIN_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static LoginPage loginPage;

    @JPage(urlTemplate = USER_PROFILE_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static UserProfilePage userProfilePage;

    @JPage(urlTemplate = PARTICIPANT_PROFILE_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static ParticipantProfilePage participantProfilePage;

    @JPage(urlTemplate = ACTIVITIES_LIST_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static ActivitiesListPage activitiesListPage;

    @JPage(urlTemplate = ACTIVITY_INSTANCE_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static ActivityInstancePage activityInstancePage;

    @JPage(urlTemplate = ACTIVITY_INSTANCE_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static PrequalifierPage activityInstancePrequalifier;

    @JPage(urlTemplate = DATE_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static DatesPage datesPage;

    @FindBy(xpath = MOBILE_MENU_ELEMENT_CONTENT)
    private static Button mobileMenu;

    @FindBy(xpath = MOBILE_MENU_POPUP_XPATH)
    private static WebElement mobileMenuPopup;

    private static boolean hasMobileMenu() {
        return mobileMenu.isDisplayed();
    }

    public static void clickLogin() {
        if (hasMobileMenu()) {
            mobileMenu.click();
        }
        shortWait.until(ExpectedConditions.visibilityOf(headerLogin.getWebElement()));
        headerLogin.click();
    }

    public static void clickLogout() {
        if (hasMobileMenu()) {
            mobileMenu.click();
        }
        shortWait.until(ExpectedConditions.visibilityOf(headerLogout.getWebElement()));
        headerLogout.click();
        if (hasMobileMenu()) {
            //Get out of mobile menu popup
            mobileMenuPopup.sendKeys(Keys.ESCAPE);
        }
    }

    public static void endTest() {
        //End test if not already logged out
        if (headerLogout.isDisplayed()) {
            clickLogout();
        }
    }
}
