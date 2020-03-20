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

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDaoFactory;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.route.IntegrationTestSuite;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class UserAuthCheckFilterTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Set<String> userGuidsToDelete = new HashSet<>();
    private static String auth0ClientId;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        auth0ClientId = testData.getTestingClient().getAuth0ClientId();
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
                    .statusCode(200);

            // Can use a whitelisted route
            given().auth().oauth2(testData.getTestingUser().getToken())
                    .when().get(profileUrl)
                    .then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("firstName", equalTo(newName));
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle.attach(UserProfileDao.class)
                    .getUserProfileSql().updateFirstName(testData.getUserId(), previousName)));
        }
    }

    @Test
    public void testTempUser_accessRouteNotInWhitelist_denied() {
        UserDto tempUser = createTempUserAndDeferCleanup();
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, tempUser.getUserGuid()));
        when().post(profileUrl).then().assertThat().statusCode(401);
    }

    @Test
    public void testTempUser_notFound_denied() {
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, "not-found"));
        when().get(profileUrl).then().assertThat().statusCode(401);
    }

    @Test
    public void testTempUser_notTemporary_denied() {
        UserDto userDto = createTempUserAndDeferCleanup();
        TransactionWrapper.useTxn(handle -> {
            // Upgrade temp user to permanent user for testing purposes.
            handle.attach(UserDao.class).upgradeUserToPermanentById(userDto.getUserId(), "fake_auth0_user_id");
        });
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, userDto.getUserGuid()));
        when().get(profileUrl).then().assertThat().statusCode(401);
    }

    @Test
    public void testTempUser_expired_denied() {
        UserDto tempUser = createTempUserAndDeferCleanup();
        TransactionWrapper.useTxn(handle -> {
            long expiredTimestamp = Instant.now().minus(2, ChronoUnit.HOURS).toEpochMilli();
            assertEquals(1, handle.attach(JdbiUser.class).updateExpiresAtById(tempUser.getUserId(), expiredTimestamp));
        });
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, tempUser.getUserGuid()));
        when().get(profileUrl).then().assertThat().statusCode(401);
    }

    @Test
    public void testTempUser_getProfile_canAccess() {
        UserDto tempUser = createTempUserAndDeferCleanup();
        String profileUrl = makeUrl(API.USER_PROFILE
                .replace(PathParam.USER_GUID, tempUser.getUserGuid()));
        when().get(profileUrl).then().assertThat().statusCode(not(401));
    }

    @Test
    public void testTempUser_getWorkflow_canAccess() {
        UserDto tempUser = createTempUserAndDeferCleanup();
        String workflowUrl = makeUrl(API.USER_STUDY_WORKFLOW
                .replace(PathParam.USER_GUID, tempUser.getUserGuid())
                .replace(PathParam.STUDY_GUID, "not-found"));
        when().get(workflowUrl).then().assertThat().statusCode(not(401));
    }

    @Test
    public void testTempUser_getActivityInstance_canAccess() {
        UserDto tempUser = createTempUserAndDeferCleanup();
        String instanceUrl = makeUrl(API.USER_ACTIVITIES_INSTANCE
                .replace(PathParam.USER_GUID, tempUser.getUserGuid())
                .replace(PathParam.STUDY_GUID, "not-found-1")
                .replace(PathParam.INSTANCE_GUID, "not-found-2"));
        when().get(instanceUrl).then().assertThat().statusCode(not(401));
    }

    @Test
    public void testTempUser_patchAndPutActivityAnswer_canAccess() {
        UserDto tempUser = createTempUserAndDeferCleanup();
        String answerUrl = makeUrl(API.USER_ACTIVITY_ANSWERS
                .replace(PathParam.USER_GUID, tempUser.getUserGuid())
                .replace(PathParam.STUDY_GUID, "not-found-1")
                .replace(PathParam.INSTANCE_GUID, "not-found-2"));
        when().patch(answerUrl).then().assertThat().statusCode(not(401));
        when().put(answerUrl).then().assertThat().statusCode(not(401));
    }

    private String makeUrl(String path) {
        return RouteTestUtil.getTestingBaseUrl() + path;
    }

    private UserDto createTempUserAndDeferCleanup() {
        UserDto tempUser = TransactionWrapper.withTxn(handle -> UserDaoFactory
                .createFromSqlConfig(RouteTestUtil.getSqlConfig())
                .createTemporaryUser(handle, auth0ClientId));
        userGuidsToDelete.add(tempUser.getUserGuid());
        return tempUser;
    }
}
