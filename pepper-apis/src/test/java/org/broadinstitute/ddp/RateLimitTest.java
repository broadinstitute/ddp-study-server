package org.broadinstitute.ddp;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.constants.RouteConstants.API.DSM_ALL_KIT_REQUESTS;
import static org.broadinstitute.ddp.route.IntegrationTestSuite.startupTestServer;
import static org.broadinstitute.ddp.route.IntegrationTestSuite.tearDown;
import static org.broadinstitute.ddp.route.IntegrationTestSuite.tearDownSuiteServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.typesafe.config.Config;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.route.IntegrationTestSuite;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.broadinstitute.ddp.util.ConfigManager;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimitTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitTest.class);

    private static String healthCheckUrl;

    private static String healthCheckPassword;

    private static Map<String, String> originalRateLimitConfig = new HashMap<>();

    @BeforeClass
    public static void cachePreviousRateLimitConfiguration() {
        Config originalConfig = ConfigManager.getInstance().getConfig();
        if (originalConfig.hasPath(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND)) {
            originalRateLimitConfig.put(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND,
                    originalConfig.getString(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND));
        }
        if (originalConfig.hasPath(ConfigFile.API_RATE_LIMIT.BURST)) {
            originalRateLimitConfig.put(ConfigFile.API_RATE_LIMIT.BURST,
                    originalConfig.getString(ConfigFile.API_RATE_LIMIT.BURST));
        }
    }

    /**
     * Before each test, we shut down and reboot the backend
     * so that we clear DOSFilter's current rate calculations.
     */
    @Before
    public void shutdownDDPAndLowerRateLimits() {
        tearDownSuiteServer(5_000);

        ConfigManager configManager = ConfigManager.getInstance();

        configManager.overrideValue(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND, "1");
        configManager.overrideValue(ConfigFile.API_RATE_LIMIT.BURST, "1");

        startupTestServer();

        healthCheckUrl = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.HEALTH_CHECK;
        healthCheckPassword = RouteTestUtil.getConfig().getString(ConfigFile.HEALTHCHECK_PASSWORD);
    }

    @Test
    public void testThingsUnderTheLimitStillGetThrough() {
        runHealthChecksConcurrentlyAndCheckResults(1, 0);
    }

    @Test
    public void testAuthFilterRunsWhenRateLimitIsEnabled() {
        String urlForRouteWithAuthFilter = RouteTestUtil.getTestingBaseUrl() + DSM_ALL_KIT_REQUESTS;
        given().when().get(urlForRouteWithAuthFilter).then().assertThat()
                .statusCode(401);
    }

    @Test
    public void testThingsOverTheLimitAreRejected() {
        runHealthChecksConcurrentlyAndCheckResults(10, 2);
    }

    @AfterClass
    public static void resetConfig() {
        tearDown();

        if (!originalRateLimitConfig.isEmpty()) {
            for (Map.Entry<String, String> originalConfig : originalRateLimitConfig.entrySet()) {
                LOG.info("Resetting rate limit {} to {}", originalConfig.getKey(), originalConfig.getValue());
                ConfigManager.getInstance().overrideValue(originalConfig.getKey(), originalConfig.getValue());
            }
        } else {
            ConfigManager.getInstance().clearOverride(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND);
            ConfigManager.getInstance().clearOverride(ConfigFile.API_RATE_LIMIT.BURST);
        }
        startupTestServer();
    }

    private static void runHealthChecksConcurrentlyAndCheckResults(int numConcurrentQueries, int minimumFailures) {
        ExecutorService executorService = Executors.newFixedThreadPool(numConcurrentQueries);
        AtomicInteger rateLimitRejects = new AtomicInteger(0);
        AtomicInteger numHealthCheckResults = new AtomicInteger(0);
        List<String> otherErrors = Collections.synchronizedList(new ArrayList<>());

        List<Callable<Boolean>> healthCheckCalls = new ArrayList<>();

        for (int i = 0; i < numConcurrentQueries; i++) {
            healthCheckCalls.add(() -> {
                HttpResponse httpResponse = null;
                try {
                    httpResponse = Request.Get(healthCheckUrl).addHeader("Host", healthCheckPassword).execute().returnResponse();
                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    LOG.info("Response {}", httpResponse.getStatusLine().getStatusCode());

                    JsonElement responseObject = null;
                    try {
                        responseObject = new JsonParser().parse(EntityUtils.toString(httpResponse.getEntity()));
                    } catch (JsonSyntaxException ignored)  { }

                    if (statusCode == HttpStatus.OK_200) {
                        if (responseObject != null) {
                            numHealthCheckResults.incrementAndGet();
                        } else {
                            otherErrors.add("Got " + statusCode + " when expecting a clean health check result");
                        }
                    } else if (statusCode == HttpStatus.TOO_MANY_REQUESTS_429) {
                        if (responseObject == null) {
                            rateLimitRejects.incrementAndGet();
                        } else {
                            otherErrors.add("Got " + statusCode + " and complete healthcheck result "
                                    + "when expecting the route to be blocked");
                        }
                    }
                } catch (IOException e) {
                    LOG.info("Rate limit query test exception", e);
                    rateLimitRejects.incrementAndGet();
                    return false;
                }
                if (httpResponse != null && httpResponse.getStatusLine().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS_429) {
                    rateLimitRejects.incrementAndGet();
                    return false;
                }
                return true;
            });
        }

        try {
            executorService.invokeAll(healthCheckCalls);
        } catch (InterruptedException e) {
            LOG.error("Interrupted while running rate limit filter test", e);
        }

        executorService.shutdown();

        Assert.assertTrue("Expected at least " + minimumFailures + " but got " + rateLimitRejects.get(),
                rateLimitRejects.get() >= minimumFailures);

        Assert.assertTrue("Unexpected results: " + StringUtils.join(otherErrors, "\n"), otherErrors.isEmpty());
    }
}
