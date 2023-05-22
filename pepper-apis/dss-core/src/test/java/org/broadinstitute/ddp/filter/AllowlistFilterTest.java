package org.broadinstitute.ddp.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static spark.Service.ignite;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.RouteImpl;
import spark.Service;
import spark.route.HttpMethod;


public class AllowlistFilterTest {

    public static Service http;
    static int PORT = 6666;
    public static final String ALLOWED = "/allowed";
    public static final String ALLOWED_TXT = "Welcome to Fantasy Island!";
    public static final String NOT_ALLOWED = "/notallowed";
    public static final String DONT_CARE = "/dontcare";
    public static final String BASEURL = "http://localhost:" + PORT;

    public static class TestServer {
        static Service startServer() {
            Service http;
            String thisIp;
            try {
                thisIp = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            AllowListFilter allowedFilter = new AllowListFilter(List.of(thisIp, "127.0.0.1", "55.444.555.555"));
            AllowListFilter notAllowedFilter = new AllowListFilter(List.of("55.444.555.555"));
            RouteImpl simpleAllowedRoute = new RouteImpl(ALLOWED) {
                @Override
                public Object handle(spark.Request request, spark.Response response) throws Exception {
                    return ALLOWED_TXT;
                }
            };

            RouteImpl simpleDeniedRoute = new RouteImpl(NOT_ALLOWED) {
                @Override
                public Object handle(spark.Request request, spark.Response response) throws Exception {
                    return "Intruder!";
                }
            };

            RouteImpl simpleUnfilteredRoute = new RouteImpl(DONT_CARE) {
                @Override
                public Object handle(spark.Request request, spark.Response response) throws Exception {
                    return "Aloha my friend!";
                }
            };
            http = ignite().port(PORT);
            http.before(ALLOWED, allowedFilter);
            http.before(NOT_ALLOWED, notAllowedFilter);
            http.addRoute(HttpMethod.get, simpleAllowedRoute);
            http.addRoute(HttpMethod.get, simpleDeniedRoute);
            http.addRoute(HttpMethod.get, simpleUnfilteredRoute);
            http.awaitInitialization();
            return http;
        }

        static void stopServer(Service http) {
            http.stop();
            http.awaitStop();
        }
    }

    @BeforeClass
    public static void startTestServer() {
        http = TestServer.startServer();
    }

    @AfterClass
    public static void stopTestServer() {
        TestServer.stopServer(http);
    }

    @Test
    public void testAllowed() throws IOException {
        Response response = Request.Get(BASEURL + ALLOWED).execute();
        assertEquals(200, response.returnResponse().getStatusLine().getStatusCode());
    }

    @Test
    public void testNotAllowed() throws IOException {
        Response res = Request.Get(BASEURL + NOT_ALLOWED).execute();
        HttpResponse response = res.returnResponse();
        assertEquals(404, response.getStatusLine().getStatusCode());
        ApiError errorBody = RouteTestUtil.parseJson(response, ApiError.class);
        assertNotNull(errorBody);
    }

    @Test
    public void testDontCare() throws IOException {
        Response response = Request.Get(BASEURL + DONT_CARE).execute();
        assertEquals(200, response.returnResponse().getStatusLine().getStatusCode());
    }
}
