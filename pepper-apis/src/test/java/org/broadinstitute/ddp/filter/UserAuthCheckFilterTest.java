package org.broadinstitute.ddp.filter;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.route.IntegrationTestSuite;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class UserAuthCheckFilterTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Set<String> userGuidsToDelete = new HashSet<>();
    private static final Gson gson = new Gson();
    private static final String INVALID_TEMP_USER_MSG = "user is not authorized";

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @AfterClass
    public static void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            int numDeleted = handle.attach(JdbiUser.class).deleteAllByGuids(userGuidsToDelete);
            assertEquals(userGuidsToDelete.size(), numDeleted);
            userGuidsToDelete.clear();
        });
    }

    @Test
    public void testAuthUser_canAccessBothAuthAndWhitelistRoutes() {
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, testData.getUserGuid()));
        String previousName = TransactionWrapper.withTxn(handle -> handle.attach(UserProfileDao.class)
                .findProfileByUserId(testData.getUserId()).get().getFirstName());

        try {
            String newName = "foo" + Instant.now().toEpochMilli();

            // Can use an auth-required route
            given().auth().oauth2(testData.getTestingUser().getToken())
                    .body(String.format("{\"firstName\":\"%s\"}", newName))
                    .when().patch(profileUrl)
                    .then().assertThat()
                    .statusCode(HttpStatus.SC_OK);

            // Can use a whitelisted route
            given().auth().oauth2(testData.getTestingUser().getToken())
                    .when().get(profileUrl)
                    .then().assertThat()
                    .statusCode(HttpStatus.SC_OK).contentType(ContentType.JSON)
                    .body("firstName", equalTo(newName));
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle.attach(UserProfileDao.class)
                    .getUserProfileSql().updateFirstName(testData.getUserId(), previousName)));
        }
    }

    @Test
    public void testTempUser_accessRouteNotInWhitelist_denied() throws Exception {
        User tempUser = createTempUserAndDeferCleanup();
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, tempUser.getGuid()));
        Response response = RouteTestUtil.buildAuthorizedPostRequest("", profileUrl, null).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        ApiError error = gson.fromJson(bodyToString, ApiError.class);
        Assert.assertEquals(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, error.getCode());
        Assert.assertEquals("Request is not in temp-user whitelist", error.getMessage());
    }

    @Test
    public void testTempUser_notFound_denied() throws Exception {
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, "not-found"));

        Response response = RouteTestUtil.buildAuthorizedGetRequest("", profileUrl).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        ApiError error = gson.fromJson(bodyToString, ApiError.class);
        Assert.assertEquals(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, error.getCode());
        Assert.assertEquals(INVALID_TEMP_USER_MSG, error.getMessage());
    }

    @Test
    public void testTempUser_notTemporary_denied() throws Exception {
        User userDto = createTempUserAndDeferCleanup();
        TransactionWrapper.useTxn(handle -> {
            // Upgrade temp user to permanent user for testing purposes.
            handle.attach(UserDao.class).upgradeUserToPermanentById(userDto.getId(), "fake_auth0_user_id");
        });
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, userDto.getGuid()));
        Response response = RouteTestUtil.buildAuthorizedGetRequest("", profileUrl).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        ApiError error = gson.fromJson(bodyToString, ApiError.class);
        Assert.assertEquals(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, error.getCode());
        Assert.assertEquals(INVALID_TEMP_USER_MSG, error.getMessage());
    }

    @Test
    public void testTempUser_expired_denied() throws Exception {
        User tempUser = createTempUserAndDeferCleanup();
        TransactionWrapper.useTxn(handle -> {
            long expiredTimestamp = Instant.now().minus(2, ChronoUnit.HOURS).toEpochMilli();
            assertEquals(1, handle.attach(JdbiUser.class).updateExpiresAtById(tempUser.getId(), expiredTimestamp));
        });
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, tempUser.getGuid()));
        Response response = RouteTestUtil.buildAuthorizedGetRequest("", profileUrl).execute();
        HttpResponse res = response.returnResponse();
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        ApiError error = gson.fromJson(bodyToString, ApiError.class);
        Assert.assertEquals(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, error.getCode());
        Assert.assertEquals(INVALID_TEMP_USER_MSG, error.getMessage());
    }

    @Test
    public void testTempUser_getProfile_canAccess() {
        User tempUser = createTempUserAndDeferCleanup();
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, tempUser.getGuid()));
        when().get(profileUrl).then().assertThat().statusCode(not(HttpStatus.SC_UNAUTHORIZED));
    }

    @Test
    public void testTempUser_getWorkflow_canAccess() {
        User tempUser = createTempUserAndDeferCleanup();
        String workflowUrl = makeUrl(API.USER_STUDY_WORKFLOW
                .replace(PathParam.USER_GUID, tempUser.getGuid())
                .replace(PathParam.STUDY_GUID, "not-found"));
        when().get(workflowUrl).then().assertThat().statusCode(not(HttpStatus.SC_UNAUTHORIZED));
    }

    @Test
    public void testTempUser_getActivityInstance_canAccess() {
        User tempUser = createTempUserAndDeferCleanup();
        String instanceUrl = makeUrl(API.USER_ACTIVITIES_INSTANCE
                .replace(PathParam.USER_GUID, tempUser.getGuid())
                .replace(PathParam.STUDY_GUID, "not-found-1")
                .replace(PathParam.INSTANCE_GUID, "not-found-2"));
        when().get(instanceUrl).then().assertThat().statusCode(not(HttpStatus.SC_UNAUTHORIZED));
    }

    @Test
    public void testTempUser_patchAndPutActivityAnswer_canAccess() {
        User tempUser = createTempUserAndDeferCleanup();
        String answerUrl = makeUrl(API.USER_ACTIVITY_ANSWERS
                .replace(PathParam.USER_GUID, tempUser.getGuid())
                .replace(PathParam.STUDY_GUID, "not-found-1")
                .replace(PathParam.INSTANCE_GUID, "not-found-2"));
        when().patch(answerUrl).then().assertThat().statusCode(not(HttpStatus.SC_UNAUTHORIZED));
        when().put(answerUrl).then().assertThat().statusCode(not(HttpStatus.SC_UNAUTHORIZED));
    }

    private String makeUrl(String path) {
        return RouteTestUtil.getTestingBaseUrl() + path;
    }

    private User createTempUserAndDeferCleanup() {
        User tempUser = TransactionWrapper.withTxn(handle ->
                handle.attach(UserDao.class).createTempUser(testData.getClientId()));
        userGuidsToDelete.add(tempUser.getGuid());
        return tempUser;
    }
}
