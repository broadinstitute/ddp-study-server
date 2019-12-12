package org.broadinstitute.ddp.pages.angiopages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByText;
import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatekeeperPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(GatekeeperPage.class);

    private static final String GATEKEEPER_CONTENT_XPATH = "//toolkit-password";
    private static final String PASSWORD_INPUT_XPATH = "//mat-form-field//input[@formcontrolname='password']";
    private static final String SUBMIT_BUTTON_TEXT = "SUBMIT";

    @FindBy(xpath = GATEKEEPER_CONTENT_XPATH)
    private WebElement gatekeeperContent;

    @FindBy(xpath = PASSWORD_INPUT_XPATH)
    private WebElement password;

    @ByText(SUBMIT_BUTTON_TEXT)
    private Button submit;

    private void verifyGatekeeperContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(gatekeeperContent));
        Assert.assertTrue(gatekeeperContent.isDisplayed());
    }

    public void setPassword(String userPassword) {
        shortWait.until(ExpectedConditions.visibilityOf(password));
        JDIPageUtils.inputText(password, userPassword);
    }

    public void clickSubmit() {
        shortWait.until(ExpectedConditions.visibilityOf(submit.getWebElement()));
        submit.click();
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
        verifyGatekeeperContentDisplayed();
    }
}
