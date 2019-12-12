package org.broadinstitute.ddp;

import static com.epam.jdi.uitests.core.interfaces.complex.tables.interfaces.CheckPageTypes.CONTAINS;
import static com.epam.jdi.uitests.core.interfaces.complex.tables.interfaces.CheckPageTypes.NONE;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.JPage;
import org.broadinstitute.ddp.pages.Auth0Page;
import org.broadinstitute.ddp.pages.angiopages.AboutYouPage;
import org.broadinstitute.ddp.pages.angiopages.ConsentPage;
import org.broadinstitute.ddp.pages.angiopages.CountMeInPage;
import org.broadinstitute.ddp.pages.angiopages.DashboardPage;
import org.broadinstitute.ddp.pages.angiopages.GatekeeperPage;
import org.broadinstitute.ddp.pages.angiopages.HomePage;
import org.broadinstitute.ddp.pages.angiopages.JoinMailingList;
import org.broadinstitute.ddp.pages.angiopages.MedicalReleaseFormPage;
import org.broadinstitute.ddp.pages.angiopages.StayInformedPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AngioAppSite extends AngioWebsite {
    private static final Logger logger = LoggerFactory.getLogger(AngioAppSite.class);

    private static final String GATEKEEPER_PAGE_URL_TEMPLATE = "/password";
    private static final String HOME_PAGE_URL_TEMPLATE = "/";
    private static final String COUNT_ME_IN_PAGE_URL_TEMPLATE = "/count-me-in";
    private static final String AUTH0_PAGE_URL_TEMPLATE = "login-test";
    private static final String ABOUT_YOU_PAGE_URL_TEMPLATE = "/about-you";
    private static final String CONSENT_PAGE_URL_TEMPLATE = "/consent";
    private static final String MEDICAL_RELEASE_PAGE_URL_TEMPLATE = "/release-survey";
    private static final String DASHBOARD_PAGE_URL_TEMPLATE = "/dashboard";
    private static final String STAY_INFORMED_PAGE_URL_TEMPLATE = "/stay-informed";

    @JPage(urlTemplate = GATEKEEPER_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static GatekeeperPage gatekeeperPage;

    @JPage(urlTemplate = HOME_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static HomePage homePage;

    @JPage(urlTemplate = COUNT_ME_IN_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static CountMeInPage countMeInPage;

    @JPage(urlTemplate = HOME_PAGE_URL_TEMPLATE, urlCheckType = NONE)
    public static JoinMailingList joinMailingList;

    @JPage(urlTemplate = AUTH0_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static Auth0Page auth0Page;

    @JPage(urlTemplate = ABOUT_YOU_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static AboutYouPage aboutYouPage;

    @JPage(urlTemplate = CONSENT_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static ConsentPage consentPage;

    @JPage(urlTemplate = MEDICAL_RELEASE_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static MedicalReleaseFormPage medicalReleaseFormPage;

    @JPage(urlTemplate = DASHBOARD_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static DashboardPage dashboardPage;

    @JPage(urlTemplate = STAY_INFORMED_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static StayInformedPage stayInformedPage;
}
