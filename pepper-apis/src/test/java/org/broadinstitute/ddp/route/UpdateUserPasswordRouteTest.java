package org.broadinstitute.ddp.route;

import com.google.gson.Gson;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.json.auth0.UpdateUserPasswordRequestPayload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.hamcrest.Matchers;

import org.junit.BeforeClass;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateUserPasswordRouteTest extends IntegrationTestSuite.TestCase {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateUserPasswordRouteTest.class);
    private static final String urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.UPDATE_USER_PASSWORD;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;

    private static String makeUrl(String userGuid) {
        return urlTemplate.replace(RouteConstants.PathParam.USER_GUID, userGuid);
    }

    private static String makePayload(String currentPassword, String password) {
        return new Gson().toJson(new UpdateUserPasswordRequestPayload(currentPassword, password));
    }

    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateBasicUserTestData(handle);
        });
        token = testData.getTestingUser().getToken();
    }

    @Test
    public void test_givenCurrentPasswordIsBlank_whenRouteIsCalled_thenItReturns422AndValidErrorCode() {
        String url = makeUrl(testData.getUserGuid());
        RestAssured.given().auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(TestData.BLANK_CURRENTPASSWORD_PAYLOAD)
                .when().patch(url).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", Matchers.is(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void test_givenPasswordIsBlank_whenRouteIsCalled_thenItReturns422AndValidErrorCode() {
        String url = makeUrl(testData.getUserGuid());
        RestAssured.given().auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(TestData.BLANK_PASSWORD_PAYLOAD)
                .when().patch(url).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", Matchers.is(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void test_givenOperatorGuidAndTokenUserDontMatch_whenRouteIsCalled_thenItReturnsUnauthorized() {
        String url = makeUrl(TestData.USER_GUID);
        RestAssured.given().auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(makePayload(TestData.CURRENT_PASSWORD, TestData.NEW_PASSWORD))
                .when().patch(url).then().assertThat()
                .statusCode(401).contentType(ContentType.JSON);
    }

    @Test
    public void test_givenUserNotAssociatedWithAuth0User_whenRouteIsCalled_thenItReturnsForbiddenAndValidErrorCode() {
        // Set auth0 user id to null
        String oldUserAuth0Id = testData.getTestingUser().getAuth0Id();
        try {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiUser.class).updateAuth0Id(testData.getUserGuid(), null));
            String url = makeUrl(testData.getUserGuid());
            RestAssured.given().auth().oauth2(token)
                    .contentType(ContentType.JSON)
                    .body(makePayload(TestData.CURRENT_PASSWORD, TestData.NEW_PASSWORD))
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
        public static final String CURRENT_PASSWORD = "a189bc987P1";
        public static final String NEW_PASSWORD = "Bf1L!h067Bxc";
        public static final String BLANK_PASSWORD_PAYLOAD = "{\"password\":\"   \"}";
        public static final String BLANK_CURRENTPASSWORD_PAYLOAD = "{\"currentPassword\":\"   \"}";
        //public static final String VALID_PAYLOAD = "{\"password\":\"" + NEW_PASSWORD + "\", \"currentPassword\":\""
        //        + CURRENT_PASSWORD + "\"}";
    }

}
