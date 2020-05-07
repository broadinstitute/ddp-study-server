package org.broadinstitute.ddp.pages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.common.TextField;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.Attribute;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByText;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Auth0Page extends DDPPage {

    private static final Logger logger = LoggerFactory.getLogger(Auth0Page.class);
    private static final String AUTH0_CONTENT = "div.auth0-lock-widget-container";
    private static final String FACEBOOK_MOBILE_URL = "m.facebook.com";
    private static final String SIGN_UP_BUTTON_TEXT = "Sign Up";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String CLASS_ATTRIBUTE = "class";
    private static final String TYPE_ATTRIBUTE = "type";
    private static final String ID_ATTRIBUTE = "id";
    private static final String DATA_PROVIDER_ATTRIBUTE = "data-provider";
    private static final String EMAIL_ATTRIBUTE = "email";
    private static final String PASSWORD_ATRRIBUTE = "password";
    private static final String AUTH0_ATTRIBUTE = "auth0-lock-submit";
    private static final String GOOGLE_AUTH0_ATTRIBUTE = "google-oauth2";
    private static final String GOOGLE_NEXT_BUTTON_TEXT = "Next";
    private static final String FACEBOOK_AUTH0_ATTRIBUTE = "facebook";
    private static final String HISTORICAL_LOGIN_AUTH0_ATTRIBUTE = "auth0";
    private static final String FACEBOOK_PASSWORD_ATTRIBUTE = "pass";
    private static final String FACEBOOK_MOBILE_EMAIL_ATTRIBUTE = "m_login_email";
    private static final String FACEBOOK_MOBILE_PASSWORD_ATTRIBUTE = "m_login_password";
    private static final String FACEBOOK_LOGIN_BUTTON_ATTRIBUTE = "loginbutton";
    private static final String FACEBOOK_MOBILE_LOGIN_BUTTON_ATTRIBUTE = "login";
    private static final String FACEBOOK_CONTINUE_BUTTON_TEXT = "CONTINUE";

    /*
     * Auth0 content
     */
    @FindBy(css = AUTH0_CONTENT)
    private WebElement auth0Content;

    /*
     * Application Login
     */
    @ByText(SIGN_UP_BUTTON_TEXT)
    private Button signUp;

    @Attribute(name = NAME_ATTRIBUTE, value = EMAIL_ATTRIBUTE)
    private TextField applicationEmail;

    @Attribute(name = NAME_ATTRIBUTE, value = PASSWORD_ATRRIBUTE)
    private TextField applicationPassword;

    @Attribute(name = CLASS_ATTRIBUTE, value = AUTH0_ATTRIBUTE)
    private Button applicationLogin;


    /*
     * Google Login -
     *  Email elements are shown on one page first and then password elements
     *   are shown on a different page
     */
    @Attribute(name = DATA_PROVIDER_ATTRIBUTE, value = GOOGLE_AUTH0_ATTRIBUTE)
    private Button loginWithGoogle;

    @Attribute(name = TYPE_ATTRIBUTE, value = EMAIL_ATTRIBUTE)
    private TextField googleEmail;

    @Attribute(name = TYPE_ATTRIBUTE, value = PASSWORD_ATRRIBUTE)
    private TextField googlePassword;

    @ByText(GOOGLE_NEXT_BUTTON_TEXT)
    private Button googlePasswordNext;

    /*
     * Facebook Login
     */
    @Attribute(name = DATA_PROVIDER_ATTRIBUTE, value = FACEBOOK_AUTH0_ATTRIBUTE)
    private Button loginWithFacebook;

    @Attribute(name = NAME_ATTRIBUTE, value = EMAIL_ATTRIBUTE)
    private TextField facebookEmail;

    @Attribute(name = ID_ATTRIBUTE, value = FACEBOOK_MOBILE_EMAIL_ATTRIBUTE)
    private TextField facebookMobileEmail;

    @Attribute(name = NAME_ATTRIBUTE, value = FACEBOOK_PASSWORD_ATTRIBUTE)
    private TextField facebookPassword;

    @Attribute(name = ID_ATTRIBUTE, value = FACEBOOK_MOBILE_PASSWORD_ATTRIBUTE)
    private TextField facebookMobilePassword;

    @Attribute(name = ID_ATTRIBUTE, value = FACEBOOK_LOGIN_BUTTON_ATTRIBUTE)
    private Button facebookInputLogin;

    @Attribute(name = NAME_ATTRIBUTE, value = FACEBOOK_MOBILE_LOGIN_BUTTON_ATTRIBUTE)
    private Button facebookMobileSubmit;

    @ByText(FACEBOOK_CONTINUE_BUTTON_TEXT)
    private Button facebookMobileContinue;

    //The logged-in-last-time-as-"user" button
    @Attribute(name = DATA_PROVIDER_ATTRIBUTE, value = HISTORICAL_LOGIN_AUTH0_ATTRIBUTE)
    private Button historicalLogin;

    public void clickSignUpNewUser() {
        shortWait.until(ExpectedConditions.visibilityOf(signUp.getWebElement()));
        signUp.click();
    }

    private void waitUntilApplicationElementsAreDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(applicationEmail.getWebElement()));
        shortWait.until(ExpectedConditions.visibilityOf(applicationPassword.getWebElement()));
        shortWait.until(ExpectedConditions.visibilityOf(applicationLogin.getWebElement()));
    }

    private void waitUntilApplicationElementsAreNotDisplayed() {
        shortWait.until(ExpectedConditions.invisibilityOf(applicationEmail.getWebElement()));
        shortWait.until(ExpectedConditions.invisibilityOf(applicationPassword.getWebElement()));
        shortWait.until(ExpectedConditions.invisibilityOf(applicationLogin.getWebElement()));
    }

    public void inputApplicationUserName(String username) {
        waitUntilApplicationElementsAreDisplayed();
        JDIPageUtils.inputText(applicationEmail.getWebElement(), username);
    }

    public void inputApplicationUserPassword(String password) {
        applicationPassword.newInput(password);
    }

    public void clickApplicationSubmit() {
        applicationLogin.click();
        JDIPageUtils.detectStalenessOfElement(applicationLogin.getLocator());
    }

    public void clickLoginWithGoogle() {
        shortWait.until(ExpectedConditions.visibilityOf(loginWithGoogle.getWebElement()));
        loginWithGoogle.click();
    }

    public void inputGoogleUserName(String username) {
        //User must input the username (pause gives 2 mins to do so) and click the submit button
        //to avoid a security challenge from google
        longWait.until(ExpectedConditions.textToBePresentInElementValue(googleEmail.getWebElement(), username));
    }

    public void inputGoogleUserPassword(String password) {
        //User must input the password (pause gives 2 mins to do so) and click the submit button
        //to avoid a security challenge from google
        longWait.until(ExpectedConditions.textToBePresentInElementValue(googlePassword.getWebElement(), password));
        longWait.until(ExpectedConditions.elementToBeSelected(googlePasswordNext.getWebElement()));
    }

    public void clickLoginWithFacebook() {
        shortWait.until(ExpectedConditions.visibilityOf(loginWithFacebook.getWebElement()));
        loginWithFacebook.click();
        JDIPageUtils.detectStalenessOfElement(loginWithFacebook.getLocator());
        logger.info("Current URL: {}", getDriver().getCurrentUrl());
        waitUntilFacebookElementsAreDisplayed();
    }

    private boolean isMobile() {
        return getDriver().getCurrentUrl().contains(FACEBOOK_MOBILE_URL);
    }

    private void clickFacebookMobileLoginContinue() {
        //For iOS Facebook login
        if (facebookMobileContinue.isDisplayed()) {
            facebookMobileContinue.click();
            shortWait.until(ExpectedConditions.invisibilityOf(facebookMobileContinue.getWebElement()));
        }
    }

    private void waitUntilFacebookElementsAreDisplayed() {
        if (isMobile()) {
            shortWait.until(ExpectedConditions.visibilityOf(facebookMobileEmail.getWebElement()));
            shortWait.until(ExpectedConditions.visibilityOf(facebookMobilePassword.getWebElement()));
            shortWait.until(ExpectedConditions.visibilityOf(facebookMobileSubmit.getWebElement()));
        } else {
            shortWait.until(ExpectedConditions.visibilityOf(facebookEmail.getWebElement()));
            shortWait.until(ExpectedConditions.visibilityOf(facebookPassword.getWebElement()));
            shortWait.until(ExpectedConditions.visibilityOf(facebookInputLogin.getWebElement()));
        }
    }

    private void waitUntilFacebookElementsAreNotDisplayed() {
        shortWait.until(ExpectedConditions.invisibilityOf(facebookEmail.getWebElement()));
        shortWait.until(ExpectedConditions.invisibilityOf(facebookPassword.getWebElement()));
        shortWait.until(ExpectedConditions.invisibilityOf(facebookInputLogin.getWebElement()));
    }

    public void inputFacebookUserName(String username) {
        if (isMobile()) {
            facebookMobileEmail.newInput(username);
        } else {
            facebookEmail.newInput(username);
        }
    }

    public void inputFacebookUserPassword(String password) {
        if (isMobile()) {
            facebookMobilePassword.newInput(password);
        } else {
            facebookPassword.newInput(password);
        }
    }

    public void clickFacebookSubmit() {
        if (isMobile()) {
            facebookMobileSubmit.click();
            JDIPageUtils.detectStalenessOfElement(facebookMobileSubmit.getLocator());
        } else {
            facebookInputLogin.click();
            JDIPageUtils.detectStalenessOfElement(facebookInputLogin.getLocator());
        }
        clickFacebookMobileLoginContinue();
    }

    public void clickHistoricalLogin() {
        shortWait.until(ExpectedConditions.visibilityOf(historicalLogin.getWebElement()));
        historicalLogin.click();
    }

    public void verifyAuth0ContentIsDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(auth0Content));
        Assert.assertTrue(auth0Content.isDisplayed());
    }

    /**
     * Waits until url is reached (and previous url is finished) before checking.
     * urlTemplate is set at BasilAppSite.java for each page object
     */
    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
        verifyAuth0ContentIsDisplayed();
    }
}
