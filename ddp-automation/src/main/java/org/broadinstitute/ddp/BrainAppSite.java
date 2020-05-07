package org.broadinstitute.ddp;

import static com.epam.jdi.uitests.core.interfaces.complex.tables.interfaces.CheckPageTypes.CONTAINS;
import static com.epam.jdi.uitests.core.interfaces.complex.tables.interfaces.CheckPageTypes.NONE;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.JPage;
import org.broadinstitute.ddp.pages.Auth0Page;
import org.broadinstitute.ddp.pages.GatekeeperPage;
import org.broadinstitute.ddp.pages.brainpages.AboutYouPage;
import org.broadinstitute.ddp.pages.brainpages.ConsentPage;
import org.broadinstitute.ddp.pages.brainpages.CountMeInPage;
import org.broadinstitute.ddp.pages.brainpages.DashboardPage;
import org.broadinstitute.ddp.pages.brainpages.HomePage;
import org.broadinstitute.ddp.pages.brainpages.JoinMailingList;
import org.broadinstitute.ddp.pages.brainpages.MedicalReleaseFormPage;
import org.broadinstitute.ddp.pages.brainpages.PostConsentSurveyPage;
import org.broadinstitute.ddp.pages.brainpages.StayInformedPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrainAppSite extends BrainWebsite {
    private static final Logger logger = LoggerFactory.getLogger(AngioAppSite.class);

    private static final String GATEKEEPER_PAGE_URL_TEMPLATE = "/password";
    private static final String HOME_PAGE_URL_TEMPLATE = "/";
    private static final String COUNT_ME_IN_PAGE_URL_TEMPLATE = "/count-me-in";
    private static final String AUTH0_PAGE_URL_TEMPLATE = "login";
    private static final String ABOUT_YOU_PAGE_URL_TEMPLATE = "/about-you";
    private static final String CONSENT_PAGE_URL_TEMPLATE = "/consent";
    private static final String MEDICAL_RELEASE_PAGE_URL_TEMPLATE = "/release-survey";
    private static final String POST_CONSENT_SURVEY_PAGE_URL_TEMPLATE = "/";
    private static final String DASHBOARD_PAGE_URL_TEMPLATE = "/dashboard";
    private static final String STAY_INFORMED_PAGE_URL_TEMPLATE = "/stay-informed";


    @JPage(urlTemplate = GATEKEEPER_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static GatekeeperPage gatekeeperPage;

    @JPage(urlTemplate = HOME_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static HomePage homePage;

    @JPage(urlTemplate = COUNT_ME_IN_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static CountMeInPage countMeInPage;

    @JPage(urlTemplate = AUTH0_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static Auth0Page auth0Page;

    @JPage(urlTemplate = ABOUT_YOU_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static AboutYouPage aboutYouPage;

    @JPage(urlTemplate = CONSENT_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static ConsentPage consentPage;

    @JPage(urlTemplate = MEDICAL_RELEASE_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static MedicalReleaseFormPage medicalReleaseFormPage;

    @JPage(urlTemplate = POST_CONSENT_SURVEY_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static PostConsentSurveyPage postConsentSurveyPage;

    @JPage(urlTemplate = DASHBOARD_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static DashboardPage dashboardPage;

    @JPage(urlTemplate = HOME_PAGE_URL_TEMPLATE, urlCheckType = NONE)
    public static JoinMailingList joinMailingList;

    @JPage(urlTemplate = STAY_INFORMED_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static StayInformedPage stayInformedPage;
}
