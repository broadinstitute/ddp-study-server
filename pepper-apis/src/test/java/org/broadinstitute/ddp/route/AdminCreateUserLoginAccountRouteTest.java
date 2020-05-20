package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.Set;

import com.auth0.json.mgmt.Connection;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.filter.StudyAdminAuthFilter;
import org.broadinstitute.ddp.filter.TokenConverterFilter;
import org.broadinstitute.ddp.json.admin.CreateUserLoginAccountPayload;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.JWTConverter;
import org.broadinstitute.ddp.service.Auth0Service;
import org.broadinstitute.ddp.transformers.NullableJsonTransformer;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.Spark;

public class AdminCreateUserLoginAccountRouteTest extends TxnAwareBaseTest {

    private static String urlTemplate;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Set<String> userGuidsToDelete = new HashSet<>();
    private static AdminCreateUserLoginAccountRoute routeSpy;

    @BeforeClass
    public static void setupServer() {
        var jsonSerializer = new NullableJsonTransformer();
        routeSpy = spy(new AdminCreateUserLoginAccountRoute());

        int port = RouteTestUtil.findOpenPortOrDefault(5559);
        Spark.port(port);
        Spark.before(RouteConstants.API.BASE + "/*", new TokenConverterFilter(new JWTConverter()));
        Spark.before(RouteConstants.API.ADMIN_BASE + "/*", new StudyAdminAuthFilter());
        Spark.post(RouteConstants.API.ADMIN_STUDY_USER_LOGIN_ACCOUNT, routeSpy, jsonSerializer);
        Spark.awaitInitialization();

        urlTemplate = "http://localhost:" + port + RouteConstants.API.ADMIN_STUDY_USER_LOGIN_ACCOUNT
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}")
                .replace(RouteConstants.PathParam.USER_GUID, "{user}");
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
            handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                    "http://localhost", testData.getAuth0ClientId(), testData.getTestingClient().getAuth0Domain());
        });
    }

    @AfterClass
    public static void tearDownServer() {
        Spark.stop();
        Spark.awaitStop();

        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                    null, testData.getAuth0ClientId(), testData.getTestingClient().getAuth0Domain());
            handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());

            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            for (var userGuid : userGuidsToDelete) {
                jdbiEnrollment.deleteByUserGuidStudyGuid(userGuid, testData.getStudyGuid());
                handle.execute("delete from user where guid = ?", userGuid);
            }
        });
    }

    @Test
    public void testStudyNotFound() {
        var payload = new CreateUserLoginAccountPayload("foo@datadonationplatform.org");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", "foo")
                .pathParam("user", "bar")
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(401);   // Admin has no access to `foo` study!
    }

    @Test
    public void testUserNotFound() {
        var payload = new CreateUserLoginAccountPayload("foo@datadonationplatform.org");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", "bar")
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void testUserNotInStudy() {
        User user = createDummyUser(null, false);
        var payload = new CreateUserLoginAccountPayload("foo@datadonationplatform.org");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", user.getGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void testUserAlreadyHasLoginAccount() {
        User user = createDummyUser("fake" + System.currentTimeMillis(), true);
        var payload = new CreateUserLoginAccountPayload("foo@datadonationplatform.org");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", user.getGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED));
    }

    @Test
    public void testClientHasNoDBConnection() {
        var mockAuth0 = mock(Auth0Service.class);
        doReturn(mockAuth0).when(routeSpy).getAuth0Service(any(), any());
        doReturn(null).when(mockAuth0).findClientDBConnection(any());

        User user = createDummyUser(null, true);
        var payload = new CreateUserLoginAccountPayload("foo@datadonationplatform.org");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", user.getGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED));
    }

    @Test
    public void testUserCreationFails() {
        var dbConn = new Connection("db", Auth0ManagementClient.DB_CONNECTION_STRATEGY);

        var mockAuth0 = mock(Auth0Service.class);
        doReturn(mockAuth0).when(routeSpy).getAuth0Service(any(), any());
        doReturn(dbConn).when(mockAuth0).findClientDBConnection(any());
        doThrow(new RuntimeException("from test")).when(mockAuth0).createUserWithPasswordTicket(any(), any(), any());

        User user = createDummyUser(null, true);
        var payload = new CreateUserLoginAccountPayload("foo@datadonationplatform.org");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", user.getGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(500);
    }

    @Test
    public void testSuccess() {
        var dbConn = new Connection("db", Auth0ManagementClient.DB_CONNECTION_STRATEGY);
        var auth0User = new com.auth0.json.mgmt.users.User();
        auth0User.setId("fake-auth0-id");
        var userWithTicket = new Auth0Service.UserWithPasswordTicket(auth0User, "the-ticket-url");

        var mockAuth0 = mock(Auth0Service.class);
        doReturn(dbConn).when(mockAuth0).findClientDBConnection(any());
        doReturn(userWithTicket).when(mockAuth0).createUserWithPasswordTicket(any(), any(), any());

        doReturn(mockAuth0).when(routeSpy).getAuth0Service(any(), any());
        doNothing().when(routeSpy).triggerEvents(any(), any());

        User user = createDummyUser(null, true);
        var payload = new CreateUserLoginAccountPayload("foo@datadonationplatform.org");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", user.getGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(201);

        verify(mockAuth0, times(1)).findClientDBConnection(testData.getAuth0ClientId());
        verify(mockAuth0, times(1)).createUserWithPasswordTicket(argThat(connection -> {
            assertEquals("db", connection.getName());
            return true;
        }), eq(payload.getEmail()), any());
        verify(routeSpy, times(1)).triggerEvents(any(), argThat(signal -> {
            assertEquals("the-ticket-url", signal.getPasswordResetTicketUrl());
            return true;
        }));

        TransactionWrapper.useTxn(handle -> {
            assertEquals("fake-auth0-id", handle.attach(UserDao.class)
                    .findUserByGuid(user.getGuid()).get().getAuth0UserId());
        });
    }

    private User createDummyUser(String fakeAuth0UserId, boolean joinStudy) {
        return TransactionWrapper.withTxn(handle -> {
            User user = handle.attach(UserDao.class).createUser(testData.getClientId(), fakeAuth0UserId);
            if (joinStudy) {
                handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                        user.getGuid(), testData.getStudyGuid(), EnrollmentStatusType.REGISTERED);
            }
            userGuidsToDelete.add(user.getGuid());
            return user;
        });
    }
}
