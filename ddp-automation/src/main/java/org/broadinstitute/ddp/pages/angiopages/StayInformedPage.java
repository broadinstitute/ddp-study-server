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

public class StayInformedPage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(StayInformedPage.class);

    private static final String STAY_INFORMED_CONTENT_XPATH = "//article[@class='PageContent']";
    private static final String RETURN_HOME_BUTTON_TEXT = "RETURN HOME";

    @FindBy(xpath = STAY_INFORMED_CONTENT_XPATH)
    private WebElement stayInformedContent;

    @ByText(RETURN_HOME_BUTTON_TEXT)
    private Button returnHome;

    private void verifyStayInformedContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(stayInformedContent));
        Assert.assertTrue(stayInformedContent.isDisplayed());
    }

    public void clickReturnHome() {
        shortWait.until(ExpectedConditions.visibilityOf(returnHome.getWebElement()));
        returnHome.click();
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
        verifyStayInformedContentDisplayed();
        //todo remove below method when page appropriately loads at top of page
        JDIPageUtils.scrollToTopOfPage();
    }
}
