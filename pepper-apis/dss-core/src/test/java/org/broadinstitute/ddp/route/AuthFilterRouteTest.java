package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_DOMAIN2;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID2;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET2;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

import com.auth0.exception.Auth0Exception;
import com.google.gson.Gson;
import com.typesafe.config.Config;
import io.restassured.RestAssured;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.json.Profile;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AuthFilterRouteTest extends IntegrationTestSuite.TestCase {

    private static final String BASE_USER_ENDPOINT = getBaseUserEndpoint(TestConstants.TEST_USER_GUID);
    private static final String PROFILE_ENDPOINT = BASE_USER_ENDPOINT + "/profile";
    private static final String STUDY_ENDPOINT = getStudyEndpoint(TestConstants.TEST_USER_GUID, TestConstants.TEST_STUDY_GUID);
    private static final String STUDY2_ENDPOINT = getStudyEndpoint(TestConstants.TEST_USER_GUID, TestConstants.SECOND_STUDY_GUID);
    private static final Header GARBAGE_AUTH_HEADER = new BasicHeader(RouteConstants.Header.AUTHORIZATION, "Bearer foo");
    private static final String EXPIRED_JWT = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlF6SkROVVJDTmtVMU5q"
            + "TkVPVEF3TjBReFF6aEZRa1EyTjBRek1VWTRNRUl3TkRGQ01EbENNUSJ9.eyJodHRwczovL2RhdGFkb25hdGlvbnBsYXRmb3J"
            + "tLm9yZy9jaWQiOiJGZ08xR080Wk1US2w0MUdPMTBRSmY2R0dzdjNMR3VSRSIsImh0dHBzOi8vZGF0YWRvbmF0aW9ucGxhdGZ"
            + "vcm0ub3JnL3VpZCI6IjE5aTMtdGVzdC11c2VyLTQ4ZjAiLCJuaWNrbmFtZSI6ImFuZHJldyIsIm5hbWUiOiJhbmRyZXdAYnJv"
            + "YWRpbnN0aXR1dGUub3JnIiwicGljdHVyZSI6Imh0dHBzOi8vcy5ncmF2YXRhci5jb20vYXZhdGFyLzVkNmQ5MDAwMWQ2MzAxNT"
            + "Y3YWIwMWEzNGFhM2MzYTBjP3M9NDgwJnI9cGcmZD1odHRwcyUzQSUyRiUyRmNkbi5hdXRoMC5jb20lMkZhdmF0YXJzJTJGYW4u"
            + "cG5nIiwidXBkYXRlZF9hdCI6IjIwMTctMTEtMDJUMDA6MDc6MjkuNDQxWiIsImVtYWlsIjoiYW5kcmV3QGJyb2FkaW5zdGl0dX"
            + "RlLm9yZyIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiaXNzIjoiaHR0cHM6Ly9kZHAtZGV2LmF1dGgwLmNvbS8iLCJzdWIiOiJhd"
            + "XRoMHw1OWVmYTI3ZmM2ZDkxZjI1MzkwMjZhOWIiLCJhdWQiOiJGZ08xR080Wk1US2w0MUdPMTBRSmY2R0dzdjNMR3VSRSIsImlh"
            + "dCI6MTUwOTU4MTI0OSwiZXhwIjoxNTA5NjE3MjQ5fQ.ZSB9KBY2IXajy_z_58zWpxjgMtGQoheQ4jmw4STTNBIU3hXEb_iJ1tRGc"
            + "bprSmyXvxHRRioMlUUdt-m1vex9TIFIxD8JGmRZnYk9MW5pJMNF4H4vGnbWiO7pdelyBLiiYM5EqSvpS2YkbU8BYjC32K42SM2"
            + "qeenjgm30E9sDT6NS1F9cwMwXFbaxvaK4sNfOWr7FNmAfccE09rJs7xaRzJ9m5pjwHdeS0VbNqHXP2RQyDFOzTbmiZ8sY8cKa"
            + "WwfHuvbQpUAlndx53BWP3bCOinmkygCza6aVtvHuo0ursy5a4UR7e9MzY9KjwDpb62FZ-ojHrDHRjj9N7P13PRjoLA";
    private static final Header EXPIRED_AUTH_HEADER = new BasicHeader(RouteConstants.Header.AUTHORIZATION,
            "Bearer " + EXPIRED_JWT);
    private static TestDataSetupUtil.GeneratedTestData generatedTestDataUser1 = null;

    private static TestDataSetupUtil.GeneratedTestData generatedTestDataUser2 = null;
    private static String testUserToken;

    private final boolean isAdmin = true;

    private static String getBaseUserEndpoint(String user) {
        return "/pepper/v1/user/" + user;
    }

    private static String getStudyEndpoint(String userGuid, String studyGuid) {
        return getBaseUserEndpoint(userGuid) + "/studies/" + studyGuid + "/activities";
    }

    @BeforeClass
    public static void setupAuthToken() throws Auth0Exception {
        testUserToken = RouteTestUtil.loginStaticTestUserForToken();
        setupUsers();
    }

    @AfterClass
    public static void deleteTestProfiles() throws SQLException, Auth0Exception {
        RouteTestUtil.deleteProfilesForUserGuid(RouteTestUtil
                .getUnverifiedUserGuidFromToken(testUserToken));
        TestDataSetupUtil.deleteGeneratedTestData();
    }

    private static void setupUsers() {
        Config cfg = ConfigManager.getInstance().getConfig();
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);

        // Client/Domain 1
        String backendTestAuth0ClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
        String backendTestSecret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
        String backendTestClientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
        String auth0domain = auth0Config.getString(ConfigFile.DOMAIN);
        String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
        String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);

        // Client/Domain 2
        String backendTestAuth0ClientId2 = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID2);
        String backendTestSecret2 = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET2);
        String backendTestClientName2 = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME2);
        String auth0domain2 = auth0Config.getString(AUTH0_DOMAIN2);
        String mgmtClientId2 = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID2);
        String mgmtSecret2 = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET2);
        String sendgridApiKey = cfg.getString(ConfigFile.SENDGRID_API_KEY);

        // Universal encryption key for sensitive DB operations
        String encryptionSecret = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);

        generatedTestDataUser1 =
                TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle,
                        true,
                        auth0domain,
                        backendTestClientName,
                        backendTestAuth0ClientId,
                        backendTestSecret,
                        encryptionSecret,
                        mgmtClientId,
                        mgmtSecret,
                        sendgridApiKey));

        generatedTestDataUser2 =
                TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle,
                        true,
                        auth0domain2,
                        backendTestClientName2,
                        backendTestAuth0ClientId2,
                        backendTestSecret2,
                        encryptionSecret,
                        mgmtClientId2,
                        mgmtSecret2,
                        sendgridApiKey));

    }

    @Before
    public void clearProfile() {
        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(TestConstants.TEST_USER_GUID);
            handle.attach(UserProfileDao.class).getUserProfileSql().deleteByUserId(userId);

        });
    }

    private void assert401NoBodyForStandardVerbs(String endpoint, Header authHeader) {
        List<Function<String, Request>> methods = List.of(Request::Get, Request::Post, Request::Patch, Request::Put);
        methods.forEach(method -> {
            try {
                method.apply(endpoint)
                        .addHeader(authHeader)
                        .execute()
                        .handleResponse(res -> {
                            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, res.getStatusLine().getStatusCode());
                            Assert.assertTrue(StringUtils.isEmpty(EntityUtils.toString(res.getEntity())));
                            return null;
                        });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void assert401(String endpoint, Header authHeader) {
        List<Function<String, Request>> methods = List.of(Request::Get, Request::Post, Request::Patch, Request::Put);
        methods.forEach(method -> {
            try {
                method.apply(endpoint)
                        .addHeader(authHeader)
                        .execute()
                        .handleResponse(res -> {
                            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, res.getStatusLine().getStatusCode());
                            return null;
                        });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private String buildProfileUrl() {
        return RouteTestUtil.getTestingBaseUrl() + PROFILE_ENDPOINT;
    }

    private String buildStudyUrl() {
        return RouteTestUtil.getTestingBaseUrl() + STUDY_ENDPOINT;
    }

    private String buildStudy2Url() {
        return RouteTestUtil.getTestingBaseUrl() + STUDY2_ENDPOINT;
    }

    private String buildGovernedStudyParticipantsUrl() {
        String url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.USER_STUDY_PARTICIPANTS;
        return url.replace(RouteConstants.PathParam.USER_GUID, TestConstants.TEST_USER_GUID)
                .replace(RouteConstants.PathParam.STUDY_GUID, TestConstants.TEST_STUDY_GUID);
    }

    @Test
    public void testTwoUsersTwoStudies() throws IOException {
        // Can user1 access study1?
        String user1IdToken = generatedTestDataUser1.getTestingUser().getToken();
        Request activitiesGetRequest = RouteTestUtil.buildAuthorizedGetRequest(user1IdToken, RouteTestUtil.getTestingBaseUrl()
                + getStudyEndpoint(generatedTestDataUser1.getUserGuid(), generatedTestDataUser1.getStudyGuid()));

        int statusCode = activitiesGetRequest.execute().returnResponse().getStatusLine().getStatusCode();
        Assert.assertTrue((statusCode == HttpStatus.SC_OK));


        // Can user2 access study2?
        String user2IdToken = generatedTestDataUser2.getTestingUser().getToken();
        activitiesGetRequest = RouteTestUtil.buildAuthorizedGetRequest(user2IdToken, RouteTestUtil.getTestingBaseUrl()
                + getStudyEndpoint(generatedTestDataUser2.getUserGuid(), generatedTestDataUser2.getStudyGuid()));

        statusCode = activitiesGetRequest.execute().returnResponse().getStatusLine().getStatusCode();
        Assert.assertTrue((statusCode == HttpStatus.SC_OK));

        // Can user1 access study2?
        activitiesGetRequest = RouteTestUtil.buildAuthorizedGetRequest(user1IdToken, RouteTestUtil.getTestingBaseUrl()
                + getStudyEndpoint(generatedTestDataUser2.getUserGuid(), generatedTestDataUser2.getStudyGuid()));

        statusCode = activitiesGetRequest.execute().returnResponse().getStatusLine().getStatusCode();
        Assert.assertTrue((statusCode == HttpStatus.SC_UNAUTHORIZED));
    }

    @Test
    public void testProfileRequestsFailsWithNoToken() {
        assert401(buildProfileUrl(), null);
    }

    @Test
    public void testProfileRequestsFailsWithGarbageToken() {
        assert401NoBodyForStandardVerbs(buildProfileUrl(), GARBAGE_AUTH_HEADER);
    }

    @Test
    public void testProfileRequestsFailsWithExpiredToken() {
        assert401NoBodyForStandardVerbs(buildProfileUrl(), EXPIRED_AUTH_HEADER);
    }

    @Test
    public void testProfileRequestsFailsWithRevokedClient() throws Exception {
        RouteTestUtil.revokeTestClient();
        assert401(buildProfileUrl(), RouteTestUtil.buildTestUserAuthHeader());
    }

    @Test
    public void testProfileRequestsFailsWithLockedUserAccount() throws Exception {
        RouteTestUtil.disableTestUserAccount(!isAdmin);
        assert401(buildProfileUrl(), RouteTestUtil.buildTestUserAuthHeader());
    }

    @Test
    public void testProfileGetRequestPassesAuthFilterWithGoodToken() throws Exception {
        String user1IdToken = generatedTestDataUser1.getTestingUser().getToken();
        Request profileGetRequest = RouteTestUtil.buildAuthorizedGetRequest(user1IdToken,
                RouteTestUtil.getTestingBaseUrl() + getBaseUserEndpoint(generatedTestDataUser1.getUserGuid()) + "/profile");

        int statusCode = profileGetRequest.execute().returnResponse().getStatusLine().getStatusCode();
        Assert.assertTrue((statusCode == HttpStatus.SC_OK));
    }

    @Test
    public void testProfilePostRequestPassesAuthFilterWithGoodToken() throws Exception {
        String profilePayload = new Gson().toJson(new Profile(null, null, "en", null, null, null));
        Request saveProfileRequest =
                RouteTestUtil.buildAuthorizedPostRequest(testUserToken, buildProfileUrl(), profilePayload);
        int statusCode = saveProfileRequest.execute().returnResponse().getStatusLine().getStatusCode();
        Assert.assertTrue((statusCode == HttpStatus.SC_CREATED));
    }

    // todo arz duplicate study and participants route security with good token, disabled client, disabled user

    // todo arz test that unmapped path errors out

    @Test
    public void testGovernedStudyParticipants_requestFailsWithNoToken() {
        assert401(buildGovernedStudyParticipantsUrl(), null);
    }

    @Test
    public void testGovernedStudyParticipants_requestFailsWithGarbageToken() {
        assert401NoBodyForStandardVerbs(buildGovernedStudyParticipantsUrl(), GARBAGE_AUTH_HEADER);
    }

    @Test
    public void testGovernedStudyParticipants_requestFailsWithExpiredToken() {
        assert401NoBodyForStandardVerbs(buildGovernedStudyParticipantsUrl(), EXPIRED_AUTH_HEADER);
    }

    @Test
    public void testGovernedStudyParticipants_requestFailsWithRevokedClient() throws Exception {
        RouteTestUtil.revokeTestClient();
        assert401(buildGovernedStudyParticipantsUrl(), RouteTestUtil.buildTestUserAuthHeader());
    }

    @Test
    public void testGovernedStudyParticipants_requestFailsWithLockedUserAccount() throws Exception {
        RouteTestUtil.disableTestUserAccount(!isAdmin);
        assert401(buildGovernedStudyParticipantsUrl(), RouteTestUtil.buildTestUserAuthHeader());
    }

    @Test
    public void testGovernedStudyParticipants_requestPassesAuthFilterWithGoodToken() {
        RestAssured.given().auth().oauth2(testUserToken)
                .when().get(buildGovernedStudyParticipantsUrl())
                .then().assertThat()
                .statusCode(Matchers.isOneOf(HttpStatus.SC_OK, HttpStatus.SC_UNPROCESSABLE_ENTITY));
    }

    @Test
    public void testGovernedStudyParticipants_failsWhenRequestedUserIsNotOperatorItself() {
        String url = buildGovernedStudyParticipantsUrl()
                .replace(TestConstants.TEST_USER_GUID, "another-user-guid");
        RestAssured.given().auth().oauth2(testUserToken)
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    public void testStudyRequestsFailsWithNoToken() {
        assert401(buildStudyUrl(), null);
    }

    @Test
    public void testStudyRequestsFailsWithGarbageToken() {
        assert401NoBodyForStandardVerbs(buildStudyUrl(), GARBAGE_AUTH_HEADER);
    }

    @Test
    public void testStudyRequestsFailsWithExpiredToken() {
        assert401(buildStudyUrl(), EXPIRED_AUTH_HEADER);
    }


    @Test
    public void testStudyRequestsFailsWithRevokedClient() throws Exception {
        RouteTestUtil.revokeTestClient();
        assert401(buildStudyUrl(), RouteTestUtil.buildTestUserAuthHeader());
    }

    @Test
    public void testStudyRequestFailsWithLockedUserAccount() throws Exception {
        RouteTestUtil.disableTestUserAccount(!isAdmin);
        assert401(buildStudyUrl(), RouteTestUtil.buildTestUserAuthHeader());
    }

    @Test
    public void testStudyRequestFailsWithClientThatDoesNotHaveAccessToStudy() throws Exception {
        assert401(buildStudy2Url(), RouteTestUtil.buildTestUserAuthHeader());
    }

    @Test
    public void testStudyActivitiesGetRequestPassesAuthFilterWithGoodToken() throws Exception {
        Request activitiesGetRequest = RouteTestUtil.buildAuthorizedGetRequest(testUserToken, buildStudyUrl());
        int statusCode = activitiesGetRequest.execute().returnResponse().getStatusLine().getStatusCode();
        Assert.assertEquals(HttpStatus.SC_OK, statusCode);
    }

    @After
    public void enableTestClientAndUser() throws SQLException {
        RouteTestUtil.enableTestClient();
        RouteTestUtil.enableTestUserAccount(!isAdmin);
        RouteTestUtil.enableTestUserAccount(isAdmin);
    }
}
