package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.CreateTemporaryUserPayload;
import org.broadinstitute.ddp.json.CreateTemporaryUserResponse;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateTemporaryUserRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Set<String> usersToDelete;
    private static Gson gson;
    private static String url;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.TEMP_USERS;
        usersToDelete = new HashSet<>();
        gson = new Gson();
    }

    @AfterClass
    public static void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            int numDeleted = handle.attach(JdbiUser.class).deleteAllByGuids(usersToDelete);
            assertEquals(usersToDelete.size(), numDeleted);
        });
    }

    @Test
    public void test_createsUser_withExpectedExpirationDuration() {
        long now = Instant.now().toEpochMilli();

        CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload(testData.getTestingClient().getAuth0ClientId());
        String body = given().body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(201).contentType(ContentType.JSON)
                .body("userGuid", notNullValue())
                .body("expiresAt", notNullValue())
                .and().extract().body().asString();
        CreateTemporaryUserResponse actual = gson.fromJson(body, CreateTemporaryUserResponse.class);
        usersToDelete.add(actual.getUserGuid());

        TransactionWrapper.useTxn(handle -> {
            UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(actual.getUserGuid());
            assertNotNull(userDto);
            assertEquals(userDto.getUserGuid(), actual.getUserGuid());

            // Compare with greater as well to account for processing/test time.
            assertTrue(actual.getExpiresAt() - now >= UserDao.EXPIRATION_DURATION_MILLIS);
            assertEquals(userDto.getExpiresAtMillis(), (Long) actual.getExpiresAt());
        });
    }

    @Test
    public void test_differentUsersEachTime() {
        CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload(testData.getTestingClient().getAuth0ClientId());

        String guid1 = given().body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(201).contentType(ContentType.JSON)
                .body("userGuid", notNullValue())
                .and().extract().path("userGuid");
        String guid2 = given().body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(201).contentType(ContentType.JSON)
                .body("userGuid", notNullValue())
                .and().extract().path("userGuid");

        usersToDelete.add(guid1);
        usersToDelete.add(guid2);

        assertNotEquals(guid1, guid2);
    }

    @Test
    public void test_missingAuth0ClientId_returns400() {
        CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload("");
        given().body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void test_invalidAuth0ClientId_returns400() {
        CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload("abc123");
        given().body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED));
    }
}
