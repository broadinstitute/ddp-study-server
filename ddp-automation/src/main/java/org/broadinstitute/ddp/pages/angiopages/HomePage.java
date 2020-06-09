package org.broadinstitute.ddp.pages.angiopages;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.simple.ByTag;
import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.pages.util.JDIPageUtils;
import org.junit.Assert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HomePage extends DDPPage {
    private static final Logger logger = LoggerFactory.getLogger(HomePage.class);

    //Header content xpaths
    private static final String HEADER_CONTENT_TAG = "mat-toolbar";
    private static final String HEADER_ANGIOSARCOMA_PROGJECT_LOGO_XPATH = "//a[@class='Header-Link']";
    private static final String HEADER_DATA_RELEASE_LINK_XPATH = "//span[@class='SimpleButton'][contains(text(), 'Data Release')]";
    private static final String HEADER_LEARN_MORE_LINK_XPATH = "//span[@class='SimpleButton'][contains(text(), 'Learn more')]";
    private static final String HEADER_COUNT_ME_IN_BUTTON_XPATH = "//span[@class='CountButton'][contains(text(), 'count me in')]";

    //Main page content xpaths
    private static final String HOMEPAGE_CONTENT_XPATH = "//div[contains(@class, 'MainContainer')]";
    private static final String HOMEPAGE_INTRODUCTION_ARROW_XPATH = "//div[@class='Intro-arrow']//a[@href='#secondView']";
    private static final String HOMEPAGE_INTRODUCTION_ARROW_RESULT_SECTION_ONE_XPATH = "//div[4]";
    private static final String HOMEPAGE_INTRODUCTION_ARROW_RESULT_SECTION_TWO_XPATH = "//div[5]";
    private static final String HOMEPAGE_INTRODUCTION_ARROW_RESULT_SECTION_THREE_XPATH = "//div[6]";

    //All count-me-in links/buttons on main page xpaths
    private static final String HOMEPAGE_TELL_US_ABOUT_YOURSELF_COUNT_ME_IN_LINK_XPATH = "//div[12]//a[@href='/count-me-in']";
    private static final String HOMEPAGE_HOW_YOU_CAN_PARTICIPATE_COUNT_ME_IN_BUTTON_XPATH = "//div[13]//a[@href='/count-me-in']";
    private static final String HOMEPAGE_PLEASE_JOIN_US_COUNT_ME_IN_BUTTON_XPATH = "//div[21]//a[@href='/count-me-in']";

    //Side-bar content xpaths (press "Learn more" in header to open)
    private static final String SIDE_BAR_CONTENT_TAG = "mat-sidenav";
    private static final String SIDE_BAR_HOME_LINK_XPATH = "//a[@class='Link'][contains(text(), 'Home')]";
    private static final String SIDE_BAR_DATA_RELEASE_LINK_XPATH = "//a[@class='Link'][contains(text(), 'Data Release')]";
    private static final String SIDE_BAR_MORE_DETAILS_FAQ_LINK_XPATH = "//a[@class='Link'][contains(text(), 'More Details/FAQ')]";
    private static final String SIDE_BAR_ABOUT_US_LINK_XPATH = "//a[@class='Link'][contains(text(), 'About Us')]";
    private static final String SIDE_BAR_JOIN_MAILING_LIST_LINK_XPATH = "//a[@class='Link'][contains(text(), 'Join Mailing List')]";
    private static final String SIDE_BAR_INFORMATION_FOR_PHYSICIANS_XPATH = "//a[@class='Link']"
            + "[contains(text(), 'Information for Physicians')]";
    private static final String SIDE_BAR_TWITTER_TIMELINE_WIDGET_XPATH = "//iframe[@id='twitter-widget-0']";

    //Footer content xpaths
    private static final String FOOTER_CONTENT_TAG = "footer";
    private static final String FOOTER_HOME_LINK_XPATH = "//a[@class='Footer-navItemLink'][contains(text(), 'Home')]";
    private static final String FOOTER_DATA_RELEASE_LINK_XPATH = "//a[@class='Footer-navItemLink'][contains(text(), 'Data Release')]";
    private static final String FOOTER_MORE_DETAILS_FAQ_LINK_XPATH = "//a[@class='Footer-navItemLink']"
            + "[contains(text(), 'More Details/FAQ')]";
    private static final String FOOTER_ABOUT_US_LINK_XPATH = "//a[@class='Footer-navItemLink'][contains(text(), 'About Us')]";
    private static final String FOOTER_JOIN_MAILING_LIST_LINK_XPATH = "//a[@class='Footer-navItemLink']"
            + "[contains(text(), 'Join Mailing List')]";
    private static final String FOOTER_INFORMATION_FOR_PHYSICIANS_LINK_XPATH = "//a[@class='Footer-navItemLink']"
            + "[contains(text(), 'Information for Physicians')]";
    private static final String FOOTER_BACK_TO_TOP_LINK_XPATH = "//a[@class='Footer-navItemLink'][contains(text(), 'Back to top')]";

    //Contact information content xpaths
    private static final String CONTACT_CONTENT_XPATH = "//div[@class='Footer-contact']";
    private static final String CONTACT_EMAIL_ADDRESS_XPATH = "//a[@class='Footer-contactLink'][contains(@href, 'mailto')]";
    private static final String CONTACT_PHONE_NUMBER_XPATH = "//a[@class='Footer-contactLink'][contains(@href, 'tel')]";
    private static final String CONTACT_FACEBOOK_XPATH = "//a[contains(@href, 'facebook')]";
    private static final String CONTACT_TWITTER_XPATH = "//a[contains(@href, 'twitter')]";


    //Header webelements
    @ByTag(HEADER_CONTENT_TAG)
    private WebElement headerContent;

    @FindBy(xpath = HEADER_ANGIOSARCOMA_PROGJECT_LOGO_XPATH)
    private WebElement angiosarcomaLogoLink;

    @FindBy(xpath = HEADER_DATA_RELEASE_LINK_XPATH)
    private WebElement headerDataReleaseLink;

    @FindBy(xpath = HEADER_LEARN_MORE_LINK_XPATH)
    private WebElement headerLearnMoreLink;

    @FindBy(xpath = HEADER_COUNT_ME_IN_BUTTON_XPATH)
    private WebElement headerCountMeInButton;


    //Main page content webelements
    @FindBy(xpath = HOMEPAGE_CONTENT_XPATH)
    private WebElement homepageContent;

    @FindBy(xpath = HOMEPAGE_INTRODUCTION_ARROW_XPATH)
    private WebElement introductionArrow;

    @FindBy(xpath = HOMEPAGE_INTRODUCTION_ARROW_RESULT_SECTION_ONE_XPATH)
    private WebElement introductionArrowResultSectionOne;

    @FindBy(xpath = HOMEPAGE_INTRODUCTION_ARROW_RESULT_SECTION_TWO_XPATH)
    private WebElement introductionArrowResultSectionTwo;

    @FindBy(xpath = HOMEPAGE_INTRODUCTION_ARROW_RESULT_SECTION_THREE_XPATH)
    private WebElement introductionArrowResultSectionThree;


    //Main page count-me-in links/buttons webelements
    @FindBy(xpath = HOMEPAGE_TELL_US_ABOUT_YOURSELF_COUNT_ME_IN_LINK_XPATH)
    private WebElement countMeInTellUsAboutYourselfLink;

    @FindBy(xpath = HOMEPAGE_HOW_YOU_CAN_PARTICIPATE_COUNT_ME_IN_BUTTON_XPATH)
    private WebElement countMeInHowYouCanParticipateButton;

    @FindBy(xpath = HOMEPAGE_PLEASE_JOIN_US_COUNT_ME_IN_BUTTON_XPATH)
    private WebElement countMeInPleaseJoinUsButton;


    //Side-bar content webelements
    @FindBy(xpath = SIDE_BAR_CONTENT_TAG)
    private WebElement sideBarContent;

    @FindBy(xpath = SIDE_BAR_HOME_LINK_XPATH)
    private WebElement sideBarHomeLink;

    @FindBy(xpath = SIDE_BAR_DATA_RELEASE_LINK_XPATH)
    private WebElement sideBarDataReleaseLink;

    @FindBy(xpath = SIDE_BAR_MORE_DETAILS_FAQ_LINK_XPATH)
    private WebElement sideBarMoreDetailsLink;

    @FindBy(xpath = SIDE_BAR_ABOUT_US_LINK_XPATH)
    private WebElement sideBarAboutUsLink;

    @FindBy(xpath = SIDE_BAR_JOIN_MAILING_LIST_LINK_XPATH)
    private WebElement sideBarJoinMailingListLink;

    @FindBy(xpath = SIDE_BAR_INFORMATION_FOR_PHYSICIANS_XPATH)
    private WebElement sideBarInformationForPhysiciansLink;

    @FindBy(xpath = SIDE_BAR_TWITTER_TIMELINE_WIDGET_XPATH)
    private WebElement sideBarTwitterTimelineWidget;


    //Footer content webelements
    @ByTag(FOOTER_CONTENT_TAG)
    private WebElement footerContent;

    @FindBy(xpath = FOOTER_HOME_LINK_XPATH)
    private WebElement footerHomeLink;

    @FindBy(xpath = FOOTER_DATA_RELEASE_LINK_XPATH)
    private WebElement footerDataReleaseLink;

    @FindBy(xpath = FOOTER_MORE_DETAILS_FAQ_LINK_XPATH)
    private WebElement footerMoreDetailsLink;

    @FindBy(xpath = FOOTER_ABOUT_US_LINK_XPATH)
    private WebElement footerAboutUsLink;

    @FindBy(xpath = FOOTER_JOIN_MAILING_LIST_LINK_XPATH)
    private WebElement footerJoinMailingListLink;

    @FindBy(xpath = FOOTER_INFORMATION_FOR_PHYSICIANS_LINK_XPATH)
    private WebElement footerInformationForPhysiciansLink;

    @FindBy(xpath = FOOTER_BACK_TO_TOP_LINK_XPATH)
    private WebElement footerBackToTopLink;


    //Contact content webelements
    @FindBy(xpath = CONTACT_CONTENT_XPATH)
    private WebElement contactContent;

    @FindBy(xpath = CONTACT_EMAIL_ADDRESS_XPATH)
    private WebElement contactEmailAddress;

    @FindBy(xpath = CONTACT_PHONE_NUMBER_XPATH)
    private WebElement contactPhoneNumber;

    @FindBy(xpath = CONTACT_FACEBOOK_XPATH)
    private WebElement contactFacebookWidget;

    @FindBy(xpath = CONTACT_TWITTER_XPATH)
    private WebElement contactTwitterWidget;

    /**
     * Clicks the very last [count me in] button since a user will likely only click
     * after getting the full explanation of the study
     */
    public void clickCountMeIn() {
        JDIPageUtils.scrollDownToElement(HOMEPAGE_PLEASE_JOIN_US_COUNT_ME_IN_BUTTON_XPATH);
        JDIPageUtils.clickButtonUsingJDI(HOMEPAGE_PLEASE_JOIN_US_COUNT_ME_IN_BUTTON_XPATH, XPATH);
    }

    public void waitUntilContentDisplayed() {
        shortWait.until(ExpectedConditions.visibilityOf(homepageContent));
        Assert.assertTrue(homepageContent.isDisplayed());
    }

    public void verifyPageIsOpened() {
        verifyOpened(urlTemplate, CHECKTYPE_CONTAINS);
        waitUntilContentDisplayed();
    }
}
