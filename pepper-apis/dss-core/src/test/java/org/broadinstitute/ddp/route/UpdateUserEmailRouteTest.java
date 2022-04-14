package org.broadinstitute.ddp.route;

import java.util.UUID;

import com.google.gson.Gson;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.json.auth0.UpdateUserEmailRequestPayload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.hamcrest.Matchers;

import org.junit.BeforeClass;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateUserEmailRouteTest extends IntegrationTestSuite.TestCase {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateUserEmailRouteTest.class);
    private static final String urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.UPDATE_USER_EMAIL;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;

    private static String makeUrl(String userGuid) {
        return urlTemplate.replace(RouteConstants.PathParam.USER_GUID, userGuid);
    }

    private static String makePayload(String email) {
        return new Gson().toJson(new UpdateUserEmailRequestPayload(email));
    }

    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateBasicUserTestData(handle);
        });
        token = testData.getTestingUser().getToken();
    }

    @Test
    public void test_givenEmailIsBlank_whenRouteIsCalled_thenItReturns422AndValidErrorCode() {
        String url = makeUrl(testData.getUserGuid());
        RestAssured.given().auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(TestData.BLANK_EMAIL_PAYLOAD)
                .when().patch(url).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", Matchers.is(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void test_givenOperatorGuidAndTokenUserDontMatch_whenRouteIsCalled_thenItReturns401() {
        String url = makeUrl(TestData.USER_GUID);
        RestAssured.given().auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(makePayload(TestData.NEW_PASSWORD))
                .when().patch(url).then().assertThat()
                .statusCode(401).contentType(ContentType.JSON);
    }

    @Test
    public void test_givenUserNotAssociatedWithAuth0User_whenRouteIsCalled_thenItReturns403AndValidErrorCode() {
        // Set auth0 user id to null
        String oldUserAuth0Id = testData.getTestingUser().getAuth0Id();
        try {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiUser.class).updateAuth0Id(testData.getUserGuid(), null));
            String url = makeUrl(testData.getUserGuid());
            RestAssured.given().auth().oauth2(token)
                    .contentType(ContentType.JSON)
                    .body(makePayload(TestData.NEW_EMAIL))
                    .when().patch(url).then().assertThat()
                    .statusCode(403).contentType(ContentType.JSON)
                    .body("code", Matchers.is(ErrorCodes.USER_NOT_ASSOCIATED_WITH_AUTH0_USER));
        } finally {
            LOG.info("Restoring old Auth0 user id {}", oldUserAuth0Id);
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiUser.class).updateAuth0Id(testData.getUserGuid(), oldUserAuth0Id));
        }
    }

    private static class TestData {
        public static final String USER_GUID = "aa0fbbc0";
        public static final String NEW_PASSWORD = "Bf1L!h067Bxc";
        // Must be unique or else Auth0 will complain
        public static final String NEW_EMAIL = UUID.randomUUID().toString() + "@bbb.com";
        public static final String BLANK_EMAIL_PAYLOAD = "{\"email\": \"   \"}";
    }

}
