package org.broadinstitute.ddp.pages.angiopages;

import com.epam.jdi.uitests.web.selenium.elements.common.Button;
import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CountMeInPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(CountMeInPage.class);

    //Count-Me-In content xpath
    private static final String CONTENT_XPATH = "//article[@class='PageContent']";

    //Contact information xpaths
    private static final String FIRST_NAME_INPUT_XPATH = "//input[@formcontrolname='firstName']";
    private static final String LAST_NAME_INPUT_XPATH = "//input[@formcontrolname='lastName']";

    //Diagnosed status xpaths - strange case of xpaths for radio button leading off page, going to use label instead
    private static final String DIAGNOSED_WITH_ANGIOSARCOMA_LABEL_XPATH = "//div/label[@for='roleTypeParticipant']";
    private static final String DIAGNOSED_WITH_ANGIOSARCOMA_ID = "roleTypeParticipant";
    private static final String NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED_LABEL_XPATH = "//div"
            + "/label[@for='roleTypeContact']";
    private static final String NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED_ID = "roleTypeContact";
    private static final String NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE_LABEL_XPATH = "//div/label[@for='roleTypeLovedOne']";
    private static final String NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE_ID = "roleTypeLovedOne";

    private static final String SUBMIT_BUTTON_XPATH = "//button[normalize-space(text())='SUBMIT']";

    //Different diagnosed statuses
    private static final String DIAGNOSED_WITH_ANGIOSARCOMA = "diagnosed with angiosarcoma";
    private static final String NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED = "not diagnosed but want to stay informed";
    private static final String NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE = "not diagnosed but lost loved one";

    @FindBy(xpath = CONTENT_XPATH)
    private WebElement content;

    @FindBy(xpath = FIRST_NAME_INPUT_XPATH)
    private WebElement firstName;

    @FindBy(xpath = LAST_NAME_INPUT_XPATH)
    private WebElement lastName;

    @FindBy(xpath = DIAGNOSED_WITH_ANGIOSARCOMA_LABEL_XPATH)
    private WebElement diagnosed;

    @FindBy(xpath = NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED_LABEL_XPATH)
    private WebElement notDiagnosedButWantToStayInformed;

    @FindBy(xpath = NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE_LABEL_XPATH)
    private WebElement notDiagnosedButLostLovedOne;

    @FindBy(xpath = SUBMIT_BUTTON_XPATH)
    private Button submit;


    public void inputFirstName(String name) {
        JDIPageUtils.scrollDownToElement(FIRST_NAME_INPUT_XPATH);
        JDIPageUtils.inputText(firstName, name);
    }

    public void inputLastName(String name) {
        JDIPageUtils.scrollDownToElement(LAST_NAME_INPUT_XPATH);
        JDIPageUtils.inputText(lastName, name);
    }

    public void selectAngiosarcomaStatus(String status) {
        if (status.equalsIgnoreCase(DIAGNOSED_WITH_ANGIOSARCOMA)) {
            logger.info("User is diagnosed with angiosarcoma");
            clickDiagnosedWithAngiosarcoma();

        } else if (status.equalsIgnoreCase(NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED)) {
            logger.info("User is not diagnosed with angiosarcoma but wants to stay informed");
            clickNotDiagnosedButWantToStayInformed();

        } else if (status.equalsIgnoreCase(NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE)) {
            logger.info("User lost a loved one to angiosarcoma");
            clickNotDiagnosedButLostLovedOne();
        }
    }

    private void clickDiagnosedWithAngiosarcoma() {
        JDIPageUtils.scrollDownToElement(DIAGNOSED_WITH_ANGIOSARCOMA_LABEL_XPATH);
        JDIPageUtils.clickElementUsingId(DIAGNOSED_WITH_ANGIOSARCOMA_ID);
    }

    private void clickNotDiagnosedButWantToStayInformed() {
        JDIPageUtils.scrollDownToElement(NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED_LABEL_XPATH);
        JDIPageUtils.clickElementUsingId(NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_WANT_TO_STAY_INFORMED_ID);
    }

    private void clickNotDiagnosedButLostLovedOne() {
        JDIPageUtils.scrollDownToElement(NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE_LABEL_XPATH);
        JDIPageUtils.clickElementUsingId(NOT_DIAGNOSED_WITH_ANGIOSARCOMA_BUT_LOST_LOVED_ONE_ID);
    }

    public void clickSubmit() {
        JDIPageUtils.scrollDownToElement(SUBMIT_BUTTON_XPATH);
        logger.info("[Submit] is displayed: {}", submit.isDisplayed());
        JDIPageUtils.clickUsingJavaScript(submit.getWebElement());
    }

    public void waitUntilContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(content));
        Assert.assertTrue(content.isDisplayed());
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
        waitUntilContentDisplayed();
    }
}
