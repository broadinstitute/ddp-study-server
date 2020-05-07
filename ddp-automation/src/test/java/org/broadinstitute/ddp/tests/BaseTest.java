package org.broadinstitute.ddp.tests;

import static com.epam.jdi.uitests.web.selenium.driver.WebDriverUtils.killAllRunWebBrowsers;
import static com.epam.jdi.uitests.web.settings.WebSettings.getDriver;

import java.io.FileReader;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.epam.jdi.uitests.web.selenium.driver.DriverTypes;
import com.epam.jdi.uitests.web.selenium.elements.composite.WebSite;
import com.epam.jdi.uitests.web.settings.WebSettings;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.ConfigFile;
import org.broadinstitute.ddp.DDPWebSite;
import org.broadinstitute.ddp.pages.DDPPage;
import org.broadinstitute.ddp.util.BrowserStackTestWatcher;
import org.broadinstitute.ddp.util.BrowserStackUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseTest {

    private static final String BUILD_PROPERTY = "org.datadonationplatform.browserStackBuildName";
    private static final String SESSION_PROPERTY = "org.datadonationplatform.browserStackSessionName";
    private AtomicReference<String> sessionId = new AtomicReference<>();

    public static BrowserStackUtil browserStackUtil = new BrowserStackUtil(
            DDPWebSite.CONFIG.getString(ConfigFile.BROWSERSTACK_USER),
            DDPWebSite.CONFIG.getString(ConfigFile.BROWSERSTACK_KEY));

    /**
     * Derived classes must declare the WebSite on which the test operates.
     */
    public abstract Class<? extends DDPWebSite> getWebSite();

    Class<? extends WebSite> webSite;

    private String currentTestName;

    @Rule
    public TestWatcher browserstackResultsHandler = new BrowserStackTestWatcher(browserStackUtil,
            (testName) -> {
                currentTestName = testName;
            },
            sessionId,
            () -> {
                hasUpdatedBrowserStackStatus = true;
            });


    private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);

    public static final String TERMINATE_ALL_BROWSERS = "org.datdonationplatform.terminateAllBrowsers";

    public static final String DO_BROWSERSTACK = "org.datadonationplatform.doBrowserStack";

    public static final String USE_SAFARI = "org.datadonationplatform.useSafari";

    public static final String FIRST_NAME_DESCRIPTION = "first name";

    public static final String LAST_NAME_DESCRIPTION = "last name";

    private static boolean useSafari = true;

    private static final String ANGIO_WEBSITE_TEMPLATE = "ascproject";

    private static final String BRAIN_WEBSITE_TEMPLATE = "braincancerproject";

    private WebDriver driver;

    private static JSONObject config;

    private static boolean isBrowserStack = false;

    public static JSONArray browserStackEnvironments;

    public static String buildName;

    private String sessionName;

    private boolean hasUpdatedBrowserStackStatus;

    @BeforeClass
    public static void setupBrowserStackCreds() throws Exception {
        try {
            isBrowserStack = Boolean.getBoolean(DO_BROWSERSTACK);


            if (isBrowserStack) {

                if (System.getProperty("config") != null) {
                    JSONParser parser = new JSONParser();
                    config = (JSONObject) parser.parse(new FileReader("src/test"
                            + "/resources/conf/" + System.getProperty("config")));
                    browserStackEnvironments = (JSONArray) config.get("environments");
                    String environments = browserStackEnvironments.toJSONString();
                    logger.info("Environments: {}", environments);
                    if (browserStackEnvironments.size() > 1) {
                        throw new RuntimeException("Only a single environment is supported by this runner,"
                                + " but the conf file has "
                                + browserStackEnvironments.size()
                                + " different configurations.");
                    }
                } else {
                    throw new RuntimeException("no config file found");
                }
            }
            buildName = System.getProperty(BUILD_PROPERTY);
            if (StringUtils.isBlank(buildName)) {
                buildName = System.getProperty("user.name");
                logger.info("Build Name: {}", buildName);
            }
        } catch (ExceptionInInitializerError error) {
            logger.info("Error info: {}", error.getCause());
        }
    }

    @Before
    public void setUp() throws Exception {
        if (isBrowserStack) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    if (isBrowserStack && !hasUpdatedBrowserStackStatus && StringUtils.isNotBlank(sessionId.get())) {
                        logger.info("Marking build as failed");
                        browserStackUtil.markBuildStatus(sessionId.get(), false, "JVM terminated");
                    }
                }
            }));

            String computerName = InetAddress.getLocalHost().getHostName();
            sessionName = System.getProperty(SESSION_PROPERTY);
            if (StringUtils.isBlank(sessionName)) {
                sessionName = computerName;
            }

            DesiredCapabilities capabilities = new DesiredCapabilities();

            Map<String, String> envCapabilities = (Map<String, String>) browserStackEnvironments.get(0);
            Iterator it = envCapabilities.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                capabilities.setCapability(pair.getKey().toString(), pair.getValue().toString());
            }

            Map<String, String> commonCapabilities = (Map<String, String>) config.get("capabilities");
            it = commonCapabilities.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pair = (Map.Entry) it.next();
                if (capabilities.getCapability(pair.getKey().toString()) == null) {
                    capabilities.setCapability(pair.getKey().toString(), pair.getValue().toString());
                }
            }
            capabilities.setCapability("build", buildName);
            capabilities.setCapability("name", sessionName + " " + currentTestName);
            capabilities.setCapability("project", "pepper");

            //Used for IE tests to ensure proper login and handling of special characters
            //IE occassionally/usually may turn the '@' character in an email into a '2'
            //causing login failure due to invalid email address
            capabilities.setCapability("requireWindowFocus", true);

            capabilities.setCapability("unicodeKeyboard", true);
            capabilities.setCapability("resetKeyboard", true);

            driver = browserStackUtil.newBrowserStackDriver(config.get("server").toString(), capabilities);

            sessionId.set(((RemoteWebDriver) driver).getSessionId().toString());

            WebSettings.initFromProperties();
            WebSettings.useDriver(() -> driver);

            //Just for logging purposes
            String currentTestConfiguration = null;

            //Just for logging purposes to follow progress of tests using console output
            if (capabilities.getCapability("browser") != null) {
                //For desktop browser
                currentTestConfiguration = capabilities.getCapability("browser").toString().toUpperCase();

            } else {
                //For mobile phone
                currentTestConfiguration = capabilities.getCapability("device").toString().toUpperCase();

            }

            logger.info("[{}] -> FINISHED SETUP", currentTestConfiguration);

        } else {
            WebSettings.initFromProperties();
            WebSettings.useDriver(DriverTypes.FIREFOX);
        }

        Class<? extends WebSite> webSite = getWebSite();
        DDPWebSite.init(webSite);
        webSite.getMethod("open").invoke(null, null);
    }

    public void displaySessionId() {
        logger.info("Session ID: {}", ((RemoteWebDriver) getDriver()).getSessionId().toString());
    }

    @After
    public void tearDown() throws Exception {
        if (isBrowserStack) {
            if (driver != null) {
                this.driver.quit();
                logger.info("Driver is null after driver.quit(): {}", driver == null);
            }
        }
        if (Boolean.getBoolean(TERMINATE_ALL_BROWSERS)) {
            killAllRunWebBrowsers();
        }
    }

    public static boolean currentPageIsAngioWebsite() {
        return getDriver().getCurrentUrl().contains(ANGIO_WEBSITE_TEMPLATE);
    }

    public static boolean currentPageIsBrainWebsite() {
        return getDriver().getCurrentUrl().contains(BRAIN_WEBSITE_TEMPLATE);
    }

    public static void waitSomeSeconds(int amountOfSecondsToWait) {
        int seconds = amountOfSecondsToWait * 1000;

        try {
            logger.info("Waiting for {} seconds...", amountOfSecondsToWait);
            Thread.sleep(seconds);

        } catch (InterruptedException error) {
            logger.info("Interrupted Exception error has occurred");
        }
    }

    public static void openPage(DDPPage page) {
        String pageUrl = WebSettings.domain + page.urlTemplate;
        getDriver().get(pageUrl);
    }

    public static void refreshPage() {
        getDriver().navigate().refresh();
    }

    public static void getCurrentPage() {
        logger.info("Current page: {}", getDriver().getCurrentUrl());
    }

    /**
     * Each test method likely requires some setup before starting.
     * This is to handle general test setup e.g. login, naviagate to page, etc.
     */
    public abstract void background();

    /**
     * Each test must end with being logged out or confirming logout status.
     */
    public abstract void endTest();

    public static void verifyCurrentUrlContainsString(String urlComponent) {
        Assert.assertTrue(getDriver().getCurrentUrl().contains(urlComponent));
    }


    public static class AutomationBuild {

        @SerializedName("name")
        public String buildName;

        @SerializedName("hashed_id")
        public String buildId;
    }

    public static class BrowserStackBuild {

        @SerializedName("automation_build")
        public AutomationBuild automationBuild;
    }

}
