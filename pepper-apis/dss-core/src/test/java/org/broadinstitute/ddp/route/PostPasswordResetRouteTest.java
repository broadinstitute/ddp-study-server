package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.QueryParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
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
    private static final String testRedirectUrlWithEmail = testRedirectUrl + "?" + QueryParam.EMAIL + "="
            + URLEncoder.encode(testEmail, StandardCharsets.UTF_8);

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(
                handle -> {
                    GeneratedTestData testData = TestDataSetupUtil.generateBasicUserTestData(handle);
                    auth0ClientId = testData.getTestingClient().getAuth0ClientId();
                    handle.attach(JdbiClient.class)
                            .updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(testRedirectUrl, auth0ClientId, auth0Domain);
                    token = testData.getTestingUser().getToken();
                    auth0Domain = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN);
                }
        );
        url = RouteTestUtil.getTestingBaseUrl() + API.POST_PASSWORD_RESET;
    }

    @Test
    public void test_WhenRouteIsCalledWithValidClientId_ItRespondsWithCorrectHttpRedirect() {
        given(TestUtil.RestAssured.nonFollowingRequestSpec())
                .queryParam(QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.EMAIL, testEmail)
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_MOVED_TEMPORARILY)
                .header(HttpHeaders.LOCATION, equalTo(testRedirectUrlWithEmail));
    }

    @Test
    public void test_WhenClientDoesNotExist_RouteRespondsWithNotFound() {
        given().queryParam(QueryParam.AUTH0_CLIENT_ID, nonExistentAuth0Client)
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.EMAIL, testEmail)
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    public void test_WhenClientDoesNotHaveRedirectUrl_RouteRespondsWithUnprocessableEntity() {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class)
                        .updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(null, auth0ClientId, auth0Domain)
        );
        given().queryParam(QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.EMAIL, testEmail)
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class)
                        .updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(testRedirectUrl, auth0ClientId, auth0Domain)
        );
    }

    @Test
    public void test_WhenRouteIsCalledWithEmptyEmail_ItRedirectsWithoutEmail() {
        given(TestUtil.RestAssured.nonFollowingRequestSpec())
                .queryParam(QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.EMAIL, "")
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_MOVED_TEMPORARILY)
                .header(HttpHeaders.LOCATION, equalTo(testRedirectUrl));
    }

    @Test
    public void test_WhenRouteIsCalledWithoutEmail_ItRedirectsWithoutEmail() {
        given(TestUtil.RestAssured.nonFollowingRequestSpec())
                .queryParam(QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_MOVED_TEMPORARILY)
                .header(HttpHeaders.LOCATION, equalTo(testRedirectUrl));
    }

    @Test
    public void test_WhenRouteIsCalledWithEmptyClientId_ItRespondsWithBadRequest() {
        given().queryParam(QueryParam.AUTH0_CLIENT_ID, "")
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.EMAIL, testEmail)
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    public void test_WhenRouteIsCalledWithEmptyAuth0Domain_andClientIdIsUnique_ItRespondsWithHttpRedirect() {
        given(TestUtil.RestAssured.nonFollowingRequestSpec())
                .queryParam(QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(QueryParam.AUTH0_DOMAIN, "")
                .queryParam(QueryParam.EMAIL, testEmail)
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_MOVED_TEMPORARILY)
                .header(HttpHeaders.LOCATION, equalTo(testRedirectUrlWithEmail));
    }

    @Test
    public void test_WhenRouteIsCalledWithEmptyAuth0Domain_andClientIdIsNotUnique_ItRespondsWithBadRequest() {
        String auth0Domain1 = "http://gkjdfkldshgf5434lsh.com";
        String auth0Domain2 = "http://mbhndyfjklvfm5553.com";
        String duplicatedAuth0ClientId = "hsdyeshrasflaelas";
        try {
            TransactionWrapper.useTxn(
                    handle -> {
                        Auth0TenantDto auth0Tenant1 = handle.attach(JdbiAuth0Tenant.class).insertIfNotExists(
                                auth0Domain1,
                                "mbvjgofhjjdsld",
                                AesUtil.encrypt("kb#jfOF@$#Jglh854jfc", EncryptionKey.getEncryptionKey())
                        );

                        Auth0TenantDto auth0Tenant2 = handle.attach(JdbiAuth0Tenant.class).insertIfNotExists(
                                auth0Domain2,
                                "nfofjfldjfglgh",
                                AesUtil.encrypt("hgudighndfjgbiud!!#hf", EncryptionKey.getEncryptionKey())
                        );

                        handle.attach(ClientDao.class).registerClient(
                                duplicatedAuth0ClientId,
                                "Hdlf(f9**8rtlJ",
                                new ArrayList<>(),
                                "84578lkf^gjHDU$",
                                auth0Tenant1.getId()
                        );

                        handle.attach(ClientDao.class).registerClient(
                                duplicatedAuth0ClientId,
                                "Mfjhf^#495J",
                                new ArrayList<>(),
                                "Mfg.fgjg($2312(^",
                                auth0Tenant2.getId()
                        );
                    }
            );
            given().queryParam(QueryParam.AUTH0_CLIENT_ID, duplicatedAuth0ClientId)
                    .queryParam(QueryParam.AUTH0_DOMAIN, "")
                    .queryParam(QueryParam.EMAIL, testEmail)
                    .queryParam(QueryParam.SUCCESS, "true")
                    .when().get(url).then().assertThat()
                    .statusCode(HttpStatus.SC_BAD_REQUEST);
        } finally {
            TransactionWrapper.useTxn(
                    handle -> {
                        handle.createUpdate(
                                "delete from client where auth0_client_id = :duplicatedAuth0ClientId"
                        ).bind("duplicatedAuth0ClientId", duplicatedAuth0ClientId).execute();
                        handle.createUpdate(
                                "delete from auth0_tenant where auth0_domain = :auth0Domain1 or auth0_domain = :auth0Domain2"
                        ).bind("auth0Domain1", auth0Domain1).bind("auth0Domain2", auth0Domain2).execute();
                    }
            );
        }
    }

    @Test
    public void test_WhenRouteIsCalledWithSuccessEqualsFalse_ItRedirectsWithEmailAndErrorCode() {
        given(TestUtil.RestAssured.nonFollowingRequestSpec())
                .queryParam(QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.EMAIL, testEmail)
                .queryParam(QueryParam.SUCCESS, "false")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_MOVED_TEMPORARILY)
                .header(HttpHeaders.LOCATION,
                        Matchers.containsString(QueryParam.ERROR_CODE + "=" + ErrorCodes.PASSWORD_RESET_LINK_EXPIRED));
    }

    @Test
    public void test_WhenRouteIsCalledWithRevokedClient_ItRespondsWithUnprocessableEntity() {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class).updateIsRevokedByAuth0ClientIdAndAuth0Domain(true, auth0ClientId, auth0Domain)
        );
        given().queryParam(QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.EMAIL, testEmail)
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
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
        given().queryParam(QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(QueryParam.AUTH0_DOMAIN, auth0Domain)
                .queryParam(QueryParam.EMAIL, testEmail)
                .queryParam(QueryParam.SUCCESS, "true")
                .when().get(url).then().assertThat()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                        testRedirectUrl, auth0ClientId, auth0Domain
                )
        );
    }
}
