package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetGovernedStudyParticipantsRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;
    private static User otherUser;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            otherUser = handle.attach(UserDao.class).createUser(testData.getClientId(), null, null);
        });
        String endpoint = RouteConstants.API.USER_STUDY_PARTICIPANTS
                .replace(RouteConstants.PathParam.USER_GUID, "{userGuid}")
                .replace(RouteConstants.PathParam.STUDY_GUID, "{studyGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @After
    public void cleanup() {
        TransactionWrapper.useTxn(handle -> handle.attach(UserGovernanceDao.class)
                .deleteAllGovernancesForProxy(testData.getUserId()));
    }

    @Test
    public void test_nonExistentStudy_returns404() {
        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", "invalid-study-guid")
                .when().get(url)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void test_noGovernedUsersInStudy() {
        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(0));
    }

    @Test
    public void test_governedUserNotGrantedStudy() {
        TransactionWrapper.useTxn(handle -> handle.attach(UserGovernanceDao.class)
                .assignProxy("test-alias", testData.getUserId(), otherUser.getId()));
        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(0));
    }

    @Test
    public void test_governedUserGrantedStudy() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            long governanceId = userGovernanceDao.assignProxy("test-alias", testData.getUserId(), otherUser.getId());
            userGovernanceDao.grantGovernedStudy(governanceId, testData.getStudyId());
        });
        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .body("[0].alias", equalTo("test-alias"))
                .body("[0].userGuid", equalTo(otherUser.getGuid()));
    }

    @Test
    public void test_inactiveGovernedUser() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            long governanceId = userGovernanceDao.assignProxy("test-alias", testData.getUserId(), otherUser.getId());
            userGovernanceDao.grantGovernedStudy(governanceId, testData.getStudyId());
            userGovernanceDao.disableProxy(governanceId);
        });
        given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", testData.getStudyGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(0));
    }
}
