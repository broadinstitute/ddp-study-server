package org.broadinstitute.ddp.filter;

import static org.broadinstitute.ddp.util.FilterUtil.whitelist;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static spark.Spark.awaitInitialization;
import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.stop;

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


public class WhitelistFilterTest {
    static int PORT = 6666;
    public static final String ALLOWED = "/allowed";
    public static final String NOT_ALLOWED = "/notallowed";
    public static final String DONT_CARE = "/dontcare";
    public static final String BASEURL = "http://localhost:" + PORT;

    public static class TestServer {
        static void startServer() {
            port(6666);
            String thisIp;
            try {
                thisIp = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            whitelist(ALLOWED, List.of(thisIp, "127.0.0.1", "55.444.555.555"));
            get(ALLOWED, (req, res) -> "Welcome to Fantasy Island!");

            whitelist(NOT_ALLOWED, List.of("55.444.555.555"));
            get("/notallowed", (req, res) -> "Intruder!");

            get(DONT_CARE, (req, res) -> "Aloha my friend!");
            awaitInitialization();
        }

        static void stopServer() {
            stop();
        }
    }

    @BeforeClass
    public static void startTestServer() {
        TestServer.startServer();
    }

    @AfterClass
    public static void stopTestServer() {
        System.out.println("Stopping server");
        TestServer.stopServer();
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
