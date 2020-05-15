package org.broadinstitute.ddp;

import static spark.Spark.awaitInitialization;
import static spark.Spark.awaitStop;
import static spark.Spark.before;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.stop;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.filter.RateLimitFilter;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimitTest {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitTest.class);
    public static final String PASS = "{1}";
    public static final String TEST_ROUTE = "/test";

    private static String valueToCaptureInOtherFilter;

    private static int DEFAULT_PORT = 6701;

    private static String localhost;

    private static final AtomicReference<String> inputFromOtherFilter = new AtomicReference<>();

    public static class TestServer {

        /**
         * Start up a server on a random port
         */
        static void startServerWithRateLimits(int apiRateLimit, int burstRateLimit) {
            int port = DEFAULT_PORT;
            ServerSocket portFinder = null;
            try {
                portFinder = new ServerSocket(0);
                port = portFinder.getLocalPort();
                portFinder.close();
            } catch (IOException e) {
                // possible race condition here if something else grabs the port
                LOG.info("Could not find available port; will use " + DEFAULT_PORT);
            }
            LOG.info("Starting test server on port {}", port);
            port(port);
            localhost =  "http://localhost:" + port + TEST_ROUTE;

            before("*", new RateLimitFilter(apiRateLimit, burstRateLimit));
            before(TEST_ROUTE, (req, res) -> {
                synchronized (inputFromOtherFilter) {
                    inputFromOtherFilter.set(req.body());
                }
            });
            post(TEST_ROUTE, (req, res) -> {
                System.out.println("oooh");
                return PASS;
            });

            awaitInitialization();
        }

        static void stopServer() {
            stop();
            awaitStop();
        }
    }

    @Before
    public void setUp() {
        valueToCaptureInOtherFilter = "filter" + System.currentTimeMillis();
        inputFromOtherFilter.set(null);
    }


    @Test
    public void testThingsUnderTheLimitStillGetThrough() {
        TestServer.startServerWithRateLimits(100, 100);
        hitRoutesConcurrentlyAndCheckResults(2, 0);
        Assert.assertEquals(valueToCaptureInOtherFilter, inputFromOtherFilter.get());
    }

    @After
    public void shutdownServer() {
        TestServer.stopServer();
    }

    @Test
    public void testAuthFilterRunsWhenRateLimitIsEnabled() {
        TestServer.startServerWithRateLimits(100, 100);
        hitRoutesConcurrentlyAndCheckResults(1, 0);
        Assert.assertEquals(valueToCaptureInOtherFilter, inputFromOtherFilter.get());
    }

    @Test
    public void testThingsOverTheLimitAreRejected() {
        TestServer.startServerWithRateLimits(1, 0);
        hitRoutesConcurrentlyAndCheckResults(10, 2);
    }

    private static void hitRoutesConcurrentlyAndCheckResults(int numConcurrentQueries, int minimumFailures) {
        ExecutorService executorService = Executors.newFixedThreadPool(numConcurrentQueries);
        AtomicInteger rateLimitRejects = new AtomicInteger(0);
        AtomicInteger numRouteResponses = new AtomicInteger(0);
        List<String> otherErrors = Collections.synchronizedList(new ArrayList<>());

        List<Callable<Boolean>> healthCheckCalls = new ArrayList<>();

        for (int i = 0; i < numConcurrentQueries; i++) {
            healthCheckCalls.add(() -> {
                HttpResponse httpResponse = null;
                try {
                    httpResponse = Request.Post(localhost).bodyString(valueToCaptureInOtherFilter, ContentType.APPLICATION_JSON)
                            .execute().returnResponse();
                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    String responseString = EntityUtils.toString(httpResponse.getEntity());

                    if (statusCode == HttpStatus.OK_200) {
                        if (PASS.equals(responseString)) {
                            numRouteResponses.incrementAndGet();
                        } else {
                            otherErrors.add("Got " + responseString + " when expecting a clean health check result");
                        }
                    } else if (statusCode == HttpStatus.TOO_MANY_REQUESTS_429) {
                        if (!PASS.equals(responseString)) {
                            rateLimitRejects.incrementAndGet();
                        } else {
                            otherErrors.add("Got " + statusCode + " and complete healthcheck result "
                                    + "when expecting the route to be blocked");
                        }
                    } else {
                        otherErrors.add("Got response " + statusCode);
                    }
                } catch (IOException e) {
                    LOG.info("Rate limit query test exception", e);
                    otherErrors.add(e.getMessage());
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

        try {
            executorService.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error("Interrupted while shutting down client threads", e);
        }

        Assert.assertTrue("Unexpected results: " + StringUtils.join(otherErrors, "\n"), otherErrors.isEmpty());

        Assert.assertTrue("Expected at least " + minimumFailures + " but got " + rateLimitRejects.get(),
                rateLimitRejects.get() >= minimumFailures);
    }
}
