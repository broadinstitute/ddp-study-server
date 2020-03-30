package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.client.Auth0ManagementClient.DB_CONNECTION_STRATEGY;
import static org.broadinstitute.ddp.client.Auth0ManagementClient.DEFAULT_DB_CONN_NAME;
import static org.broadinstitute.ddp.client.Auth0ManagementClient.KEY_MIN_LENGTH;
import static org.broadinstitute.ddp.client.Auth0ManagementClient.KEY_PASSWORD_COMPLEXITY_OPTIONS;
import static org.broadinstitute.ddp.client.Auth0ManagementClient.KEY_PASSWORD_POLICY;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.auth0.exception.APIException;
import com.auth0.json.mgmt.Connection;

import io.restassured.http.ContentType;

import org.broadinstitute.ddp.client.ApiResult;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.study.PasswordPolicy;
import org.broadinstitute.ddp.model.study.PasswordPolicy.PolicyType;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import spark.HaltException;

public class GetStudyPasswordPolicyRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String auth0ClientId;
    private static String auth0Domain;
    private static String url;

    private Auth0ManagementClient mockMgmt;
    private GetStudyPasswordPolicyRoute routeSpy;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        auth0ClientId = testData.getTestingClient().getAuth0ClientId();
        auth0Domain = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN);
        url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.STUDY_PASSWORD_POLICY
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}");
    }

    @Before
    public void init() {
        mockMgmt = mock(Auth0ManagementClient.class);
        routeSpy = spy(new GetStudyPasswordPolicyRoute());
        doReturn(mockMgmt).when(routeSpy).createManagementClient(any());
    }

    @Test
    public void testHandle_noClientIdQueryParam() {
        given().pathParam("study", testData.getStudyGuid())
                .queryParam(RouteConstants.QueryParam.AUTH0_DOMAIN, auth0Domain)
                .when().get(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("clientId"));
    }

    @Test
    public void testHandle_noDomainQueryParam_clientDoesNotExist() {
        given().pathParam("study", testData.getStudyGuid())
                .queryParam(RouteConstants.QueryParam.AUTH0_CLIENT_ID, "foobar")
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_FOUND))
                .body("message", containsString("does not exist"));
    }

    @Test
    public void testHandle_noDomainQueryParam_clientExistsAndIsNotUnique() {
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
            given().pathParam("study", testData.getStudyGuid())
                    .queryParam(RouteConstants.QueryParam.AUTH0_CLIENT_ID, duplicatedAuth0ClientId)
                    .when().get(url)
                    .then().assertThat()
                    .statusCode(400).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                    .body("message", containsString("is not unique"));
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
    public void testHandle_invalidStudy() {
        given().pathParam("study", "foobar")
                .queryParam(RouteConstants.QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(RouteConstants.QueryParam.AUTH0_DOMAIN, auth0Domain)
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_FOUND))
                .body("message", containsString("foobar"));
    }

    @Test
    public void testHandle_invalidClient() {
        given().pathParam("study", testData.getStudyGuid())
                .queryParam(RouteConstants.QueryParam.AUTH0_CLIENT_ID, "foobar")
                .queryParam(RouteConstants.QueryParam.AUTH0_DOMAIN, auth0Domain)
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_FOUND))
                .body("message", containsString("foobar"));
    }

    @Test
    public void testHandle_clientNotPermittedForStudy() {
        StudyDto anotherStudy = TransactionWrapper.withTxn(handle ->
                TestDataSetupUtil.generateTestStudy(handle, RouteTestUtil.getConfig()));
        given().pathParam("study", anotherStudy.getGuid())
                .queryParam(RouteConstants.QueryParam.AUTH0_CLIENT_ID, auth0ClientId)
                .queryParam(RouteConstants.QueryParam.AUTH0_DOMAIN, auth0Domain)
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_FOUND))
                .body("message", containsString(auth0ClientId));
    }

    @Test
    public void testLookupPasswordPolicy() {
        var conn = createDummyFairConnection();
        when(mockMgmt.listClientConnections(any())).thenReturn(ApiResult.ok(200, List.of(conn)));

        PasswordPolicy actual = routeSpy.lookupPasswordPolicy(null, "client");
        assertNotNull(actual);
        assertEquals(PolicyType.FAIR, actual.getType());
        assertEquals(32, actual.getMinLength());
    }

    @Test
    public void testLookupPasswordPolicy_onlyUseDbConnection() {
        var conn1 = createDummyFairConnection();
        var conn2 = new Connection("foo", "some-social");
        when(mockMgmt.listClientConnections(any())).thenReturn(ApiResult.ok(200, List.of(conn1, conn2)));

        PasswordPolicy actual = routeSpy.lookupPasswordPolicy(null, "client");
        assertEquals(PolicyType.FAIR, actual.getType());
    }

    @Test
    public void testLookupPasswordPolicy_multipleDbConnections_useDefault() {
        var conn1 = createDummyFairConnection();
        var conn2 = new Connection("foo", DB_CONNECTION_STRATEGY);
        conn2.setOptions(Map.of(KEY_PASSWORD_POLICY, PolicyType.GOOD.name().toLowerCase()));
        when(mockMgmt.listClientConnections(any())).thenReturn(ApiResult.ok(200, List.of(conn1, conn2)));

        PasswordPolicy actual = routeSpy.lookupPasswordPolicy(null, "client");
        assertEquals(PolicyType.FAIR, actual.getType());
    }

    @Test
    public void testLookupPasswordPolicy_defaultToNoneWhenNoPolicy() {
        var conn = createDummyFairConnection();
        conn.setOptions(Map.of());
        when(mockMgmt.listClientConnections(any())).thenReturn(ApiResult.ok(200, List.of(conn)));

        PasswordPolicy actual = routeSpy.lookupPasswordPolicy(null, "client");
        assertEquals(PolicyType.NONE, actual.getType());
    }

    @Test
    public void testLookupPasswordPolicy_noDbConnections() {
        var conn = new Connection("foo", "only-social");
        when(mockMgmt.listClientConnections(any())).thenReturn(ApiResult.ok(200, List.of(conn)));

        PasswordPolicy actual = routeSpy.lookupPasswordPolicy(null, "client");
        assertNull(actual);
    }

    @Test
    public void testLookupPasswordPolicy_unknownPolicy() {
        var conn = createDummyFairConnection();
        conn.setOptions(Map.of(KEY_PASSWORD_POLICY, "foobar"));
        when(mockMgmt.listClientConnections(any())).thenReturn(ApiResult.ok(200, List.of(conn)));
        try {
            routeSpy.lookupPasswordPolicy(null, "client");
            fail("expected exception not thrown");
        } catch (HaltException e) {
            assertEquals(500, e.statusCode());
            assertTrue(e.body().contains(ErrorCodes.SERVER_ERROR));
        }
    }

    @Test
    public void testLookupPasswordPolicy_overLengthLimit() {
        var conn = createDummyFairConnection();
        conn.setOptions(Map.of(KEY_PASSWORD_COMPLEXITY_OPTIONS, Map.of(KEY_MIN_LENGTH, 256)));
        when(mockMgmt.listClientConnections(any())).thenReturn(ApiResult.ok(200, List.of(conn)));
        try {
            routeSpy.lookupPasswordPolicy(null, "client");
            fail("expected exception not thrown");
        } catch (HaltException e) {
            assertEquals(500, e.statusCode());
            assertTrue(e.body().contains(ErrorCodes.SERVER_ERROR));
        }
    }

    @Test
    public void testLookupPasswordPolicy_errorWhileLookup() {
        when(mockMgmt.listClientConnections(any()))
                .thenReturn(ApiResult.err(400, new APIException(Map.of(), 400)));
        try {
            routeSpy.lookupPasswordPolicy(null, "client");
            fail("expected exception not thrown");
        } catch (HaltException e) {
            assertEquals(500, e.statusCode());
            assertTrue(e.body().contains(ErrorCodes.SERVER_ERROR));
        }
    }

    @Test
    public void testLookupPasswordPolicy_exceptionWhileLookup() {
        when(mockMgmt.listClientConnections(any()))
                .thenReturn(ApiResult.thrown(new IOException("from test")));
        try {
            routeSpy.lookupPasswordPolicy(null, "client");
            fail("expected exception not thrown");
        } catch (HaltException e) {
            assertEquals(500, e.statusCode());
            assertTrue(e.body().contains(ErrorCodes.SERVER_ERROR));
        }
    }

    private Connection createDummyFairConnection() {
        var conn = new Connection(DEFAULT_DB_CONN_NAME, DB_CONNECTION_STRATEGY);
        conn.setOptions(Map.of(
                KEY_PASSWORD_POLICY, PolicyType.FAIR.name().toLowerCase(),
                KEY_PASSWORD_COMPLEXITY_OPTIONS, Map.of(KEY_MIN_LENGTH, 32)));
        return conn;
    }
}
