package org.broadinstitute.ddp.pages.brainpages;

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

public class JoinMailingList extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(JoinMailingList.class);

    private static final String JOIN_MALING_LIST_POPUP_XPATH = "//mat-dialog-container//toolkit-join-mailing-list";
    private static final String FIRST_NAME_XPATH = "//toolkit-join-mailing-list//input[@formcontrolname='firstName']";
    private static final String LAST_NAME_XPATH = "//toolkit-join-mailing-list//input[@formcontrolname='lastName']";
    private static final String EMAIL_ADDRESS_XPATH = "//toolkit-join-mailing-list//input[@formcontrolname='email']";
    private static final String CONFIRM_EAIL_ADDRESS_XPATH = "//toolkit-join-mailing-list//input[@formcontrolname='confirmEmail']";
    private static final String CANCEL_BUTTON_TEXT = "CANCEL";
    private static final String JOIN_BUTTON_XPATH = "//button[normalize-space(text()) = 'JOIN']";

    @FindBy(xpath = JOIN_MALING_LIST_POPUP_XPATH)
    private WebElement joinMailingListPopup;

    @FindBy(xpath = FIRST_NAME_XPATH)
    private WebElement firstName;

    @FindBy(xpath = LAST_NAME_XPATH)
    private WebElement lastName;

    @FindBy(xpath = EMAIL_ADDRESS_XPATH)
    private WebElement emailAddress;

    @FindBy(xpath = CONFIRM_EAIL_ADDRESS_XPATH)
    private WebElement confirmedEmailAddress;

    @ByText(CANCEL_BUTTON_TEXT)
    private Button cancel;

    @FindBy(xpath = JOIN_BUTTON_XPATH)
    private Button join;


    private void verifyJoinMailingListPopupDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(joinMailingListPopup));
        Assert.assertTrue(joinMailingListPopup.isDisplayed());
    }

    public void setFirstName(String name) {
        shortWait.until(ExpectedConditions.visibilityOf(firstName));
        JDIPageUtils.inputText(firstName, name);
    }

    public void verifyFirstNameWasSet(String name) {
        shortWait.until(ExpectedConditions.visibilityOf(firstName));
        String text = JDIPageUtils.getWebElementTextUsingValueAttribute(firstName);
        Assert.assertTrue(text.equals(name));
    }

    public void setLastName(String name) {
        shortWait.until(ExpectedConditions.visibilityOf(lastName));
        JDIPageUtils.inputText(lastName, name);
    }

    public void verifyLastNameWasSet(String name) {
        shortWait.until(ExpectedConditions.visibilityOf(lastName));
        String text = JDIPageUtils.getWebElementTextUsingValueAttribute(lastName);
        Assert.assertTrue(text.equals(name));
    }

    public void setEmailAddress(String email) {
        shortWait.until(ExpectedConditions.visibilityOf(emailAddress));
        JDIPageUtils.inputText(emailAddress, email);
    }

    public void setConfirmedEmailAddress(String email) {
        shortWait.until(ExpectedConditions.visibilityOf(confirmedEmailAddress));
        JDIPageUtils.inputText(confirmedEmailAddress, email);

        String initialInputEmail = JDIPageUtils.getWebElementTextUsingValueAttribute(emailAddress);
        String confirmedEmail = JDIPageUtils.getWebElementTextUsingValueAttribute(confirmedEmailAddress);
        Assert.assertTrue(confirmedEmail.equals(initialInputEmail));
        logger.info("Initial email [{}] vs. Confirmed email [{}]", initialInputEmail, confirmedEmail);
    }

    public void clickCancel() {
        shortWait.until(ExpectedConditions.visibilityOf(cancel.getWebElement()));
        cancel.click();
    }

    public void clickJoin() {
        shortWait.until(ExpectedConditions.visibilityOf(join.getWebElement()));
        JDIPageUtils.clickButtonUsingJDI("//button[contains(text(), 'JOIN')]", XPATH);

        //Wait for the invisibility of the button so the database has time to update
        //Note: waiting for staleness of WebElement will not work
        //shortWait.until(ExpectedConditions.invisibilityOf(join.getWebElement()));
    }

    /**
     * Since this is not a page but a popup - this just confirms the popup can be seen
     */
    public void verifyPageIsOpened() {
        verifyJoinMailingListPopupDisplayed();
    }
}
