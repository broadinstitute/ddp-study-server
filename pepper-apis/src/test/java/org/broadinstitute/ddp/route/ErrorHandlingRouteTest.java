package org.broadinstitute.ddp.route;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorHandlingRouteTest extends IntegrationTestSuite.TestCase {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandlingRouteTest.class);
    private static String url;

    @BeforeClass
    public static void setup() throws Exception {
        url = RouteTestUtil.getTestingBaseUrl();
    }

    @Test
    public void test404ErrorCaptured() throws Exception {
        HttpResponse response = Request.Get(url + "/abc/xyz").execute().returnResponse();
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
        Assert.assertEquals("Content-Type: application/json",
                response.getFirstHeader(HttpHeaders.CONTENT_TYPE).toString());
        ApiError apiError = RouteTestUtil.parseJson(response, ApiError.class);
        Assert.assertEquals("NOT_FOUND", apiError.getCode());
    }

    @Test
    public void test500ErrorCaptured() throws Exception {
        HttpResponse response = Request.Get(url + "/error").execute().returnResponse();
        Assert.assertEquals(500, response.getStatusLine().getStatusCode());
        Assert.assertEquals("Content-Type: application/json",
                response.getFirstHeader(HttpHeaders.CONTENT_TYPE).toString());
        ApiError apiError = RouteTestUtil.parseJson(response, ApiError.class);
        Assert.assertEquals("SERVER_ERROR", apiError.getCode());
    }
}
