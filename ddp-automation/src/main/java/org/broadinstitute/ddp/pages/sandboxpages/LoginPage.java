package org.broadinstitute.ddp.pages.sandboxpages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.common.Input;
import com.epam.jdi.uitests.web.selenium.elements.common.Text;
import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginPage extends DDPPage {

    private static final String LOGIN_CAPTION = "LOG IN";
    private static final String LOGOUT_EVENT_MESSAGE = "logout event occurs";
    private static final String LOGOUT_CAPTION = "LOG OUT";

    private static final Logger logger = LoggerFactory.getLogger(LoginPage.class);
    //logger.info("[SANDBOX-LOGIN]");

    @FindBy(xpath = "//mat-card-content//ddp-login//button[@data-ddp-test='loginButton']")
    public Button login;

    @FindBy(xpath = "//mat-card-content//ddp-login//button[@data-ddp-test='logoutButton']")
    public Button logout;

    @FindBy(xpath = "//*[@data-ddp-test='loginCaptionInput']//input")
    public Input loginCaptionInput;

    @FindBy(xpath = "//*[@data-ddp-test='logoutCaptionInput']//input")
    public Input logoutCaptionInput;

    @FindBy(xpath = "//div[@data-ddp-test='loginEvent']")
    public Text logoutEvent;

    @FindBy(xpath = "//button[@data-ddp-test='callServerButton']")
    public Button makeServerCall;

    @FindBy(xpath = "//div[@data-ddp-test='profileJson']")
    public Text profileData;

    public void verifyLoginButtonDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(login.getWebElement()));
        Assert.assertTrue(login.isDisplayed());
        verifyLoginCaption();
        Assert.assertFalse(logout.isDisplayed());
    }

    public void clickLogin() {
        shortWait.until(ExpectedConditions.visibilityOf(login.getWebElement()));
        login.click();
        //loginWait.until(ExpectedConditions.invisibilityOf(login.getWebElement()));
    }

    /**
     * Check for defeault caption
     */
    public void verifyLoginCaption() {
        shortWait.until(ExpectedConditions.visibilityOf(login.getWebElement()));
        verifyLoginCaption(LOGIN_CAPTION);
    }

    /**
     * Check for custom caption
     *
     * @param caption custom caption for button
     */
    public void verifyLoginCaption(String caption) {
        shortWait.until(ExpectedConditions.visibilityOf(login.getWebElement()));
        String text = JDIPageUtils.getWebElementText(login.getWebElement());
        JDIPageUtils.verifyTextMatch(caption, text);
    }

    public void verifyLogoutButtonDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(logout.getWebElement()));
        Assert.assertTrue(logout.isDisplayed());
        verifyLogoutCaption();
        Assert.assertFalse(login.isDisplayed());
    }

    public void clickLogout() {
        shortWait.until(ExpectedConditions.visibilityOf(logout.getWebElement()));
        logout.click();
        //loginWait.until(ExpectedConditions.invisibilityOf(logout.getWebElement()));
    }

    /**
     * Check for defeault caption
     */
    public void verifyLogoutCaption() {
        shortWait.until(ExpectedConditions.visibilityOf(logout.getWebElement()));
        verifyLogoutCaption(LOGOUT_CAPTION);
    }

    /**
     * Check for custom caption
     *
     * @param caption custom caption for button
     */
    public void verifyLogoutCaption(String caption) {
        shortWait.until(ExpectedConditions.visibilityOf(logout.getWebElement()));
        String text = JDIPageUtils.getWebElementText(logout.getWebElement());
        JDIPageUtils.verifyTextMatch(caption, text);
    }

    public void inputLoginCaption(String input) {
        shortWait.until(ExpectedConditions.visibilityOf(loginCaptionInput.getWebElement()));
        JDIPageUtils.inputText(loginCaptionInput.getWebElement(), input);
    }

    public void inputLogoutCaption(String input) {
        shortWait.until(ExpectedConditions.visibilityOf(logoutCaptionInput.getWebElement()));
        logoutCaptionInput.clear();
        logoutCaptionInput.newInput(input);
    }

    public void verifyLogoutEvent() {
        shortWait.until(ExpectedConditions.visibilityOf(logoutEvent.getWebElement()));
        logoutEvent.waitText(LOGOUT_EVENT_MESSAGE);
        Assert.assertTrue(logoutEvent.getText().equals(LOGOUT_EVENT_MESSAGE));
    }

    public void clickMakeServerCall() {
        shortWait.until(ExpectedConditions.visibilityOf(makeServerCall.getWebElement()));
        makeServerCall.click();
    }

    public void verifiyProfileData(String input) {
        shortWait.until(ExpectedConditions.visibilityOf(profileData.getWebElement()));
        profileData.waitText(input);
        Assert.assertTrue(profileData.getText().contains(input));
    }

    public void verifyPageIsOpened() {
        this.verifyOpened(this.urlTemplate, CHECKTYPE_CONTAINS);
    }
}
