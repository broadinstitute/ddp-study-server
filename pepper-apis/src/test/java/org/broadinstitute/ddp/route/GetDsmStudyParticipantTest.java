package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetDsmStudyParticipantTest extends DsmRouteTest {

    private static String url;
    private static String legacyAltPid = "12345.GUID-GUID-GUID";
    private static String TEST_LEGACY_SHORTID = "12345.LEGACY-SHORTID";

    @BeforeClass
    public static void setupRouteTest() {
        String endpoint = RouteConstants.API.DSM_STUDY_PARTICIPANT
                .replace(RouteConstants.PathParam.STUDY_GUID, "{studyGuid}")
                .replace(RouteConstants.PathParam.USER_GUID, "{userGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;

        // Set enrollment and fold in an Altpid!
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.setUserEnrollmentStatus(handle,
                    generatedTestData,
                    EnrollmentStatusType.REGISTERED);
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid, "
                    + " legacy_shortid = :legacyShortId where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("legacyShortId", TEST_LEGACY_SHORTID)
                    .bind("guid", userGuid)
                    .execute());
        });
    }

    @AfterClass
    public static void cleanupRouteTest() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = null, "
                    + " legacy_shortid = null where guid = :guid")
                    .bind("guid", userGuid)
                    .execute());
        });
    }

    @Test
    public void test_nonExistentStudy_returns404() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", "non-existent-study")
                .pathParam("userGuid", userGuid)
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_FOUND))
                .body("message", containsString("non-existent-study was not found"));
    }

    @Test
    public void test_nonExistentParticipant_returns404() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyGuid)
                .pathParam("userGuid", "foo-bar-user")
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_FOUND))
                .body("message", containsString("foo-bar-user"));
    }

    @Test
    public void test_providingAltPid_getsUser() {
        doLookupWithId(legacyAltPid)
                .and().body("participantId", equalTo(legacyAltPid));
    }

    @Test
    public void test_providingGuid_getsUser() {
        doLookupWithId(userGuid)
                .and().body("participantId", equalTo(userGuid));
    }

    @Test
    public void test_providingGuid_getsConsentSuspendedUser() {
        TransactionWrapper.useTxn(handle ->
                handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                        userGuid, studyGuid, EnrollmentStatusType.CONSENT_SUSPENDED));
        doLookupWithId(userGuid)
                .and().body("participantId", equalTo(userGuid));
    }

    private ValidatableResponse doLookupWithId(String userGuidOrLegacyAltPid) {
        return (given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyGuid)
                .pathParam("userGuid", userGuidOrLegacyAltPid)
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("firstName", equalTo(TestConstants.TEST_USER_PROFILE_FIRST_NAME))
                .and().body("lastName", equalTo(TestConstants.TEST_USER_PROFILE_LAST_NAME))
                .and().body("country", equalTo(TestConstants.TEST_USER_MAIL_ADDRESS_COUNTRY))
                .and().body("shortId", equalTo(TestConstants.TEST_USER_HRUID))
                .and().body("legacyShortId", equalTo(TEST_LEGACY_SHORTID))
                .and().body("validAddress", equalTo("2"))
                .and().body("city", equalTo(TestConstants.TEST_USER_MAIL_ADDRESS_CITY))
                .and().body("postalCode", equalTo(TestConstants.TEST_USER_MAIL_ADDRESS_ZIP))
                .and().body("street1", equalTo(TestConstants.TEST_USER_MAIL_ADDRESS_STREET_NAME))
                .and().body("street2", equalTo(TestConstants.TEST_USER_MAIL_ADDRESS_STREET_APT))
                .and().body("state", equalTo(TestConstants.TEST_USER_MAIL_ADDRESS_STATE)));
    }
}
