package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.HashMap;
import java.util.Map;

import okhttp3.HttpUrl;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.QueryParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil.GeneratedTestData;
import org.broadinstitute.ddp.util.TestUtil;

import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;

public class PostPasswordResetRouteTest extends IntegrationTestSuite.TestCase {

    private static String token;
    private static String url;
    private static String auth0ClientId;
    private static String nonExistentAuth0Client = "1010";
    private static String auth0Domain;
    private static final String testEmail = "test_user@datadonationplatform.org";
    private static final String testRedirectUrl = "http://www.datadonationplatform.org/default-password-reset-page/";
    private static final String testRedirectUrlWithEmail = testRedirectUrl + "?" + QueryParam.EMAIL + "=" + testEmail;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(
                handle -> {
                    GeneratedTestData testData = TestDataSetupUtil.generateBasicUserTestData(handle);
                    auth0ClientId = testData.getTestingClient().getAuth0ClientId();
                    handle.attach(JdbiClient.class)
                            .updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(testRedirectUrl, auth0ClientId, auth0Domain);
                    token = testData.getTestingUser().getToken();
                    auth0Domain = ConfigManager.getInstance().getConfig().getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN);
                }
        );
        url = RouteTestUtil.getTestingBaseUrl() + API.POST_PASSWORD_RESET;
    }

    @Test
    public void test_WhenRouteIsCalledWithValidClientId_ItRespondsWithCorrectHttpRedirect() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, auth0ClientId);
        queryParams.put(QueryParam.AUTH0_DOMAIN, auth0Domain);
        queryParams.put(QueryParam.EMAIL, testEmail);
        queryParams.put(QueryParam.SUCCESS, "true");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given(TestUtil.RestAssured.nonFollowingRequestSpec())
                .when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_MOVED_TEMPORARILY)
                .header(HttpHeaders.LOCATION, equalTo(testRedirectUrlWithEmail));
    }

    @Test
    public void test_WhenClientDoesNotExist_RouteRespondsWithNotFound() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, nonExistentAuth0Client);
        queryParams.put(QueryParam.AUTH0_DOMAIN, auth0Domain);
        queryParams.put(QueryParam.EMAIL, testEmail);
        queryParams.put(QueryParam.SUCCESS, "true");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given().when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void test_WhenClientDoesNotHaveRedirectUrl_RouteRespondsWithUnprocessableEntity() {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class)
                        .updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(null, auth0ClientId, auth0Domain)
        );
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, auth0ClientId);
        queryParams.put(QueryParam.AUTH0_DOMAIN, auth0Domain);
        queryParams.put(QueryParam.EMAIL, testEmail);
        queryParams.put(QueryParam.SUCCESS, "true");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given().when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class)
                        .updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(testRedirectUrl, auth0ClientId, auth0Domain)
        );
    }

    @Test
    public void test_WhenRouteIsCalledWithEmptyEmail_ItRespondsWithBadRequest() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, auth0ClientId);
        queryParams.put(QueryParam.AUTH0_DOMAIN, auth0Domain);
        queryParams.put(QueryParam.EMAIL, "");
        queryParams.put(QueryParam.SUCCESS, "true");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given().when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void test_WhenRouteIsCalledWithEmptyClientId_ItRespondsWithBadRequest() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, "");
        queryParams.put(QueryParam.AUTH0_DOMAIN, auth0Domain);
        queryParams.put(QueryParam.EMAIL, testEmail);
        queryParams.put(QueryParam.SUCCESS, "true");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given().when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void test_WhenRouteIsCalledWithEmptyAuth0Domain_ItRespondsWithBadRequest() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, auth0ClientId);
        queryParams.put(QueryParam.AUTH0_DOMAIN, "");
        queryParams.put(QueryParam.EMAIL, testEmail);
        queryParams.put(QueryParam.SUCCESS, "true");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given().when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void test_WhenRouteIsCalledWithSuccessEqualsFalse_ItRedirectsWithEmailAndErrorCode() {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, auth0ClientId);
        queryParams.put(QueryParam.AUTH0_DOMAIN, auth0Domain);
        queryParams.put(QueryParam.EMAIL, testEmail);
        queryParams.put(QueryParam.SUCCESS, "false");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given(TestUtil.RestAssured.nonFollowingRequestSpec())
                .when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_MOVED_TEMPORARILY)
                .header(HttpHeaders.LOCATION,
                        Matchers.containsString(QueryParam.ERROR_CODE + "=" + ErrorCodes.PASSWORD_RESET_LINK_EXPIRED));
    }

    @Test
    public void test_WhenRouteIsCalledWithRevokedClient_ItRespondsWithUnprocessableEntity() {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class).updateIsRevokedByAuth0ClientIdAndAuth0Domain(true, auth0ClientId, auth0Domain)
        );
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, auth0ClientId);
        queryParams.put(QueryParam.AUTH0_DOMAIN, auth0Domain);
        queryParams.put(QueryParam.EMAIL, testEmail);
        queryParams.put(QueryParam.SUCCESS, "true");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given().when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class).updateIsRevokedByAuth0ClientIdAndAuth0Domain(false, auth0ClientId, auth0Domain)
        );
    }

    @Test
    public void test_WhenRedirectUrlIsMalformed_RouteRespondsWithInternalServerError() {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                        "malformedUrl", auth0ClientId, auth0Domain
                )
        );
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(QueryParam.AUTH0_CLIENT_ID, auth0ClientId);
        queryParams.put(QueryParam.AUTH0_DOMAIN, auth0Domain);
        queryParams.put(QueryParam.EMAIL, testEmail);
        queryParams.put(QueryParam.SUCCESS, "true");
        HttpUrl fullUrl = buildEncodedUrl(url, queryParams);
        given().when().get(fullUrl.toString()).then().assertThat()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                        testRedirectUrl, auth0ClientId, auth0Domain
                )
        );
    }

    private static HttpUrl buildEncodedUrl(String url, Map<String, String> queryParams) {
        HttpUrl.Builder builder = HttpUrl.parse(url).newBuilder();
        queryParams.forEach((paramName, paramValue) -> builder.addQueryParameter(paramName, paramValue));
        return builder.build();
    }

}
