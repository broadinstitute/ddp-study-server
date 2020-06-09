package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.CreateTemporaryUserPayload;
import org.broadinstitute.ddp.json.CreateTemporaryUserResponse;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateTemporaryUserRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Set<String> usersToDelete;
    private static Gson gson;
    private static String url;
    private static String auth0Domain;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.TEMP_USERS;
        usersToDelete = new HashSet<>();
        gson = new Gson();
        auth0Domain = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0).getString(ConfigFile.DOMAIN);
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

        CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload(
                testData.getTestingClient().getAuth0ClientId(),
                auth0Domain
        );
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
        CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload(
                testData.getTestingClient().getAuth0ClientId(),
                auth0Domain
        );

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
    public void test_missingAuth0Domain_returns400_ifClientIsNotUnique() {
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
            CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload(duplicatedAuth0ClientId, null);
            given().body(payload, ObjectMapperType.GSON)
                    .when().post(url)
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
    public void test_missingAuth0ClientId_returns400() {
        CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload("", "");
        given().body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void test_invalidAuth0ClientId_returns400() {
        CreateTemporaryUserPayload payload = new CreateTemporaryUserPayload("abc123", "auth0domain");
        given().body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED));
    }
}
