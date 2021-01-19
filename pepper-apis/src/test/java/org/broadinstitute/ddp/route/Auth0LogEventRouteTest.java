package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.broadinstitute.ddp.constants.Auth0LogEventTestConstants.AUTH0_LOG_EVENT_TESTDATA1;
import static org.broadinstitute.ddp.constants.Auth0LogEventTestConstants.AUTH0_LOG_EVENT_TESTDATA_NEGATIVE1;
import static org.broadinstitute.ddp.constants.ErrorCodes.DATA_PERSIST_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.MISSING_BODY;
import static org.broadinstitute.ddp.constants.ErrorCodes.REQUIRED_PARAMETER_MISSING;
import static org.broadinstitute.ddp.route.Auth0LogEventRoute.QUERY_PARAM_TENANT;
import static org.broadinstitute.ddp.util.TestUtil.readJSONFromFile;
import static org.hamcrest.Matchers.equalTo;

import java.io.FileNotFoundException;


import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.junit.BeforeClass;
import org.junit.Test;


public class Auth0LogEventRouteTest extends IntegrationTestSuite.TestCase {

    private static final String AUTH0_TEST_TENANT = "ddp-test";
    private static final String MANDATORY_URL_PARAMS = '?' + QUERY_PARAM_TENANT + '=' + AUTH0_TEST_TENANT;
    private static final String RESPONSE_BODY_PARAM_CODE = "code";

    private static String urlNoParams;
    private static String urlWithParams;

    @BeforeClass
    public static void setup() {
        // create negative (without params) and positive (with params) urls
        urlNoParams = RouteTestUtil.getTestingBaseUrl() + API.AUTH0_LOG_EVENT;
        urlWithParams = urlNoParams + MANDATORY_URL_PARAMS;
    }

    @Test
    public void testAuth0LogEventPositive() throws FileNotFoundException {
        given()
                .body(readJSONFromFile(AUTH0_LOG_EVENT_TESTDATA1).toString())
                .when().post(urlWithParams)
                .then().assertThat()
                .statusCode(SC_OK);
    }

    @Test
    public void testAuth0LogEventNegativeDueNotSpecifiedTenantParam() throws FileNotFoundException {
        given()
                .body(readJSONFromFile(AUTH0_LOG_EVENT_TESTDATA1).toString())
                .when().post(urlNoParams)
                .then().assertThat()
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body(RESPONSE_BODY_PARAM_CODE, equalTo(REQUIRED_PARAMETER_MISSING));
    }

    @Test
    public void testAuth0LogEventNegativeDueBodyIsEmpty() throws FileNotFoundException {
        given()
                .when().post(urlWithParams)
                .then().assertThat()
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body(RESPONSE_BODY_PARAM_CODE, equalTo(MISSING_BODY));
    }

    @Test
    public void testAuth0LogEventNegativeDueBodyContainsNoType() throws FileNotFoundException {
        given()
                .body(readJSONFromFile(AUTH0_LOG_EVENT_TESTDATA_NEGATIVE1).toString())
                .when().post(urlWithParams)
                .then().assertThat()
                .statusCode(SC_INTERNAL_SERVER_ERROR)
                .contentType(JSON)
                .body(RESPONSE_BODY_PARAM_CODE, equalTo(DATA_PERSIST_ERROR));
    }
}
