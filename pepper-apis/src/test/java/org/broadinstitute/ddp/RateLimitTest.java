package org.broadinstitute.ddp;

import static org.broadinstitute.ddp.route.IntegrationTestSuite.startupTestServer;
import static org.broadinstitute.ddp.route.IntegrationTestSuite.tearDown;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.typesafe.config.Config;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
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

    private static String url;

    private static String password;

    private static Map<String, String> originalRateLimitConfig = new HashMap<>();

    @BeforeClass
    public static void cachePreviousRateLimitConfiguration() {
        Config originalConfig = ConfigManager.getInstance().getConfig();
        if (originalConfig.hasPath(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND)) {
            originalRateLimitConfig.put(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND, originalConfig.getString(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND));
        }
        if (originalConfig.hasPath(ConfigFile.API_RATE_LIMIT.BURST)) {
            originalRateLimitConfig.put(ConfigFile.API_RATE_LIMIT.BURST, originalConfig.getString(ConfigFile.API_RATE_LIMIT.BURST));
        }
    }

    /**
     * Before each test, we shut down and reboot the backend
     * so that we clear DOSFilter's current rate calculations.
     */
    @Before
    public void shutdownDDPAndLowerRateLimits() {
        tearDown();

        ConfigManager configManager = ConfigManager.getInstance();

        configManager.overrideValue(ConfigFile.API_RATE_LIMIT.MAX_QUERIES_PER_SECOND, "1");
        configManager.overrideValue(ConfigFile.API_RATE_LIMIT.BURST, "1");

        startupTestServer();

        url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.HEALTH_CHECK;
        password = RouteTestUtil.getConfig().getString(ConfigFile.HEALTHCHECK_PASSWORD);
    }

    @Test
    public void testThingsUnderTheLimitStillGetThrough() {
        runHealthChecksConcurrentlyAndCheckResults(1, 0);
    }

    @Test
    public void testThingsOverTheLimitAreRejected() {
        runHealthChecksConcurrentlyAndCheckResults(10, 2);
    }

    @AfterClass
    public static void resetConfig() {
        tearDown();
        for (Map.Entry<String, String> originalConfig : originalRateLimitConfig.entrySet()) {
            LOG.info("Resetting rate limit {} to {}", originalConfig.getKey(), originalConfig.getValue());
            ConfigManager.getInstance().overrideValue(originalConfig.getKey(), originalConfig.getValue());
        }
        startupTestServer();
    }

    private static void runHealthChecksConcurrentlyAndCheckResults(int numConcurrentQueries, int minimumFailures) {
        ExecutorService executorService = Executors.newFixedThreadPool(numConcurrentQueries);
        AtomicInteger rateLimitRejects = new AtomicInteger(0);

        List<Callable<Boolean>> healthCheckCalls = new ArrayList<>();

        for (int i = 0; i < numConcurrentQueries; i++) {
            healthCheckCalls.add(() -> {
               HttpResponse httpResponse = null;
               try {
                   httpResponse = Request.Get(url).addHeader("Host", password).execute().returnResponse();
                   LOG.info(httpResponse.getStatusLine().getReasonPhrase());
               } catch (IOException e) {
                   LOG.info("Rate limit query test exception", e);
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
    }
}
