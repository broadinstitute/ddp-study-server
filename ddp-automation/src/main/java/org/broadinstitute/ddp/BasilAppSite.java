package org.broadinstitute.ddp;

import static com.epam.jdi.uitests.core.interfaces.complex.tables.interfaces.CheckPageTypes.CONTAINS;

import com.epam.jdi.uitests.web.selenium.elements.pageobjects.annotations.JPage;
import org.broadinstitute.ddp.pages.Auth0Page;
import org.broadinstitute.ddp.pages.basilpages.ConsentPage;
import org.broadinstitute.ddp.pages.basilpages.DashboardPage;
import org.broadinstitute.ddp.pages.basilpages.DeclinedConsentPage;
import org.broadinstitute.ddp.pages.basilpages.HomePage;
import org.broadinstitute.ddp.pages.basilpages.LandingPage;
import org.broadinstitute.ddp.pages.basilpages.NotQualifiedPage;
import org.broadinstitute.ddp.pages.basilpages.PrequalifierPage;
import org.broadinstitute.ddp.pages.basilpages.WelcomePage;


public class BasilAppSite extends BasilAppWebsite {
    private static final String HOME_PAGE_URL_TEMPLATE = "/";
    private static final String LANDING_PAGE_URL_TEMPLATE = "/landing";
    private static final String WELCOME_PAGE_URL_TEMPLATE = "/welcome";
    private static final String AUTH0_PAGE_URL_TEMPLATE = "login-dev";
    private static final String PREQUALIFIER_PAGE_URL_TEMPLATE = "/prequalifier";
    private static final String PREQUALIFIER_PAGE_ACTIVITY_URL_TEMPLATE = "/activity";
    private static final String CONSENT_PAGE_URL_TEMPLATE = "/consent";
    private static final String DASHBOARD_PAGE_URL_TEMPLATE = "/dashboard";
    private static final String CONSENT_DECLINED_PAGE_URL_TEMPLATE = "/consent-declined";
    private static final String NOT_QUALIFIED_PAGE_URL_TEMPLATE = "/not-qualified";

    @JPage(HOME_PAGE_URL_TEMPLATE)
    public static HomePage homePage;

    @JPage(urlTemplate = LANDING_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static LandingPage landingPage;

    @JPage(urlTemplate = WELCOME_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static WelcomePage welcomePage;

    @JPage(urlTemplate = AUTH0_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static Auth0Page auth0Page;

    @JPage(urlTemplate = PREQUALIFIER_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static PrequalifierPage prequalifierPage;

    @JPage(urlTemplate = PREQUALIFIER_PAGE_ACTIVITY_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static PrequalifierPage prequalifierActivityPage;

    @JPage(urlTemplate = CONSENT_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static ConsentPage consentPage;

    @JPage(urlTemplate = DASHBOARD_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static DashboardPage dashboardPage;

    @JPage(urlTemplate = CONSENT_DECLINED_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static DeclinedConsentPage declinedConsentPage;

    @JPage(urlTemplate = NOT_QUALIFIED_PAGE_URL_TEMPLATE, urlCheckType = CONTAINS)
    public static NotQualifiedPage notQualifiedPage;

}
