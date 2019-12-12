package org.broadinstitute.ddp.util;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.tests.BaseTest;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utilities for interacting with browserstack
 */
public class BrowserStackUtil {

    private static final Logger LOG = LoggerFactory.getLogger(BrowserStackUtil.class);

    private static final String AUTOMATE_REVIEW_BUILD_URL_BASE = "https://www.browserstack.com/automate/builds/";
    public static final String DEVICE_CAPABILITY = "device";

    private final String browserStackAutomateUser;

    private final String browserStackAutomateKey;

    public BrowserStackUtil(String browerStackAutomateUser, String browserStackAutomateKey) {
        this.browserStackAutomateUser = browerStackAutomateUser;
        this.browserStackAutomateKey = browserStackAutomateKey;
    }

    private URI buildApiUrl(String path) throws URISyntaxException {
        return new URI("https://" + browserStackAutomateUser + ":" + browserStackAutomateKey + "@api.browserstack.com" + path);
    }

    /**
     * Creates a new Driver that will run at browserstack
     */
    public RemoteWebDriver newBrowserStackDriver(String server, DesiredCapabilities capabilities)
            throws MalformedURLException {
        return new BrowserStackDeviceAwareDriver(new URL("https://" + browserStackAutomateUser + ":"
                + browserStackAutomateKey + "@" + server + "/wd/hub"), capabilities);
    }

    /**
     * WebDriver that senses whether it's running via browserstack and ignores
     * window sizing/maximize calls that would otherwise error out since
     * mobile devices don't let you set window size.  This is all a workaround
     * because JDI's initialization attempts to set/maximize browser window.
     */
    private class BrowserStackDeviceAwareDriver extends RemoteWebDriver {

        private final boolean isMobileDevice;

        public BrowserStackDeviceAwareDriver(URL url, DesiredCapabilities capabilities) {
            super(url, capabilities);
            Object device = capabilities.getCapability(DEVICE_CAPABILITY);
            if (device != null) {
                isMobileDevice = StringUtils.isNotBlank((String) device);
            } else {
                isMobileDevice = false;
            }
        }

        @Override
        public Options manage() {
            Options options = super.manage();
            return new BrowserStackDeviceAwareOptions(options);
        }

        public class BrowserStackDeviceAwareOptions implements Options {
            private final Options options;

            public BrowserStackDeviceAwareOptions(Options options) {
                this.options = options;
            }

            @Override
            public void addCookie(Cookie cookie) {
                options.addCookie(cookie);
            }

            @Override
            public void deleteCookieNamed(String s) {
                options.deleteCookieNamed(s);
            }

            @Override
            public void deleteCookie(Cookie cookie) {
                options.deleteCookie(cookie);
            }

            @Override
            public void deleteAllCookies() {
                options.deleteAllCookies();
            }

            @Override
            public Set<Cookie> getCookies() {
                return options.getCookies();
            }

            @Override
            public Cookie getCookieNamed(String s) {
                return options.getCookieNamed(s);
            }

            @Override
            public Timeouts timeouts() {
                return options.timeouts();
            }

            @Override
            public ImeHandler ime() {
                return options.ime();
            }

            @Override
            public Window window() {
                return new BrowserStackDeviceAwareWindow(options.window());
            }


            @Override
            public Logs logs() {
                return options.logs();
            }


        }

        public class BrowserStackDeviceAwareWindow implements Window {

            private final Window window;

            public BrowserStackDeviceAwareWindow(Window window) {
                this.window = window;
            }

            private void logAppiumUnsupportedWarning(String methodName, Exception e) {
                LOG.debug("Ignoring {} on physical mobile device since it's unsupported with appium."
                        + "  Stack trace follows.", methodName, e);
            }

            @Override
            public void setSize(Dimension dimension) {
                try {
                    window.setSize(dimension);
                } catch (WebDriverException e) {
                    if (isMobileDevice) {
                        logAppiumUnsupportedWarning("setSize()", e);
                    } else {
                        throw e;
                    }
                }
            }

            @Override
            public void setPosition(Point point) {
                try {
                    window.setPosition(point);
                } catch (WebDriverException e) {
                    if (isMobileDevice) {
                        logAppiumUnsupportedWarning("setPosition()", e);
                    } else {
                        throw e;
                    }
                }
            }

            @Override
            public Dimension getSize() {
                return window.getSize();
            }

            @Override
            public Point getPosition() {
                return window.getPosition();
            }

            @Override
            public void maximize() {
                try {
                    window.maximize();
                } catch (WebDriverException e) {
                    if (isMobileDevice) {
                        logAppiumUnsupportedWarning("maximize()", e);
                    } else {
                        throw e;
                    }
                }
            }

            @Override
            public void fullscreen() {
                try {
                    window.maximize();
                } catch (WebDriverException e) {
                    if (isMobileDevice) {
                        logAppiumUnsupportedWarning("fullscreen()", e);
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    /**
     * Sets the status of the build session
     */
    public void markBuildStatus(String sessionId, boolean passed, String message) {
        try {
            URI uri = buildApiUrl("/automate/sessions/" + sessionId + ".json");
            HttpPut putRequest = new HttpPut(uri);

            ArrayList<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
            nameValuePairs.add((new BasicNameValuePair("status", passed ? "completed" : "error")));
            nameValuePairs.add((new BasicNameValuePair("reason", message)));
            putRequest.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            CloseableHttpResponse response = HttpClientBuilder.create().build().execute(putRequest);
            int responseCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            if (responseCode != 200) {
                throw new RuntimeException("Attempt to update browserstack build status for " + sessionId
                        + " failed with " + responseBody);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not mark browserstack build status for " + sessionId, e);
        }
    }

    /**
     * Generates the url to use in your browser to get more information about the given build name
     */
    public String getBrowserStackAutomateReviewUrl(String buildName) throws Exception {
        URI uri = buildApiUrl("/automate/builds.json");
        HttpGet getBuilds = new HttpGet(uri);

        CloseableHttpResponse response = HttpClientBuilder.create().build().execute(getBuilds);
        int responseCode = response.getStatusLine().getStatusCode();
        String responseBody = EntityUtils.toString(response.getEntity());
        if (responseCode != 200) {
            throw new RuntimeException("Attempt to get browserstack build " + buildName
                    + " failed with " + responseBody);
        }

        BaseTest.BrowserStackBuild[] builds = new Gson().fromJson(responseBody, BaseTest.BrowserStackBuild[].class);
        String buildId = null;

        for (BaseTest.BrowserStackBuild build : builds) {
            BaseTest.AutomationBuild autoBuild = build.automationBuild;
            if (buildName.equals(autoBuild.buildName)) {
                buildId = autoBuild.buildId;
            }
        }

        return AUTOMATE_REVIEW_BUILD_URL_BASE + buildId;
    }
}
