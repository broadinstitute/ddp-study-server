package org.broadinstitute.ddp.route;


import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.broadinstitute.ddp.constants.ErrorCodes.DATA_PERSIST_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.MISSING_BODY;
import static org.broadinstitute.ddp.constants.SendGridEventTestConstants.SENDGRID_EVENT_TESTDATA;
import static org.broadinstitute.ddp.constants.SendGridEventTestConstants.SENDGRID_EVENT_TESTDATA_NEGATIVE;
import static org.broadinstitute.ddp.util.TestUtil.readJsonArrayFromFile;
import static org.hamcrest.Matchers.equalTo;

import java.io.FileNotFoundException;


import org.broadinstitute.ddp.constants.RouteConstants;
import org.junit.BeforeClass;
import org.junit.Test;

public class SendGridEventRouteTest extends IntegrationTestSuite.TestCase {

    private static final String RESPONSE_BODY_PARAM_CODE = "code";

    private static String url;

    @BeforeClass
    public static void setup() {
        url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.SENDGRID_EVENT;
    }

    @Test
    public void testSendGridEventPositive() throws FileNotFoundException {
        given()
                .body(readJsonArrayFromFile(SENDGRID_EVENT_TESTDATA).toString())
                .when().post(url)
                .then().assertThat()
                .statusCode(SC_OK);
    }

    @Test
    public void testSendGridEventNegativeDueBodyIsEmpty() {
        given()
                .when().post(url)
                .then().assertThat()
                .statusCode(SC_BAD_REQUEST)
                .contentType(JSON)
                .body(RESPONSE_BODY_PARAM_CODE, equalTo(MISSING_BODY));
    }

    @Test
    public void testSendGridEventNegativeDueBodyNotContainsMandatoryData() throws FileNotFoundException {
        given()
                .body(readJsonArrayFromFile(SENDGRID_EVENT_TESTDATA_NEGATIVE).toString())
                .when().post(url)
                .then().assertThat()
                .statusCode(SC_INTERNAL_SERVER_ERROR)
                .contentType(JSON)
                .body(RESPONSE_BODY_PARAM_CODE, equalTo(DATA_PERSIST_ERROR));
    }
}
