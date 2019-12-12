package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_GUID;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_DAY;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_MONTH;
import static org.broadinstitute.ddp.constants.TestConstants.TEST_USER_PROFILE_BIRTH_YEAR;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetDsmMedicalRecordRouteTest extends DsmRouteTest {

    private static final long EPOCH_MILLIS_DIAGNOSIS = 264124800000L;
    private static String url;
    private static String legacyAltPid = "12345.GUID-GUID-GUID";

    @BeforeClass
    public static void testDataSetup() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.REGISTERED);
            TestDataSetupUtil.addTestConsent(handle, generatedTestData);
            TestDataSetupUtil.addEnrollmentHook(handle, generatedTestData);
            TestDataSetupUtil.answerTestConsent(handle,
                    true,
                    true,
                    true,
                    TEST_USER_PROFILE_BIRTH_DAY,
                    TEST_USER_PROFILE_BIRTH_MONTH,
                    TEST_USER_PROFILE_BIRTH_YEAR,
                    generatedTestData);
            TestDataSetupUtil.addAboutYou(handle, generatedTestData);
            TestDataSetupUtil.answerAboutYou(handle, EPOCH_MILLIS_DIAGNOSIS, generatedTestData);

            String endpoint = RouteConstants.API.DSM_PARTICIPANT_MEDICAL_INFO
                    .replace(RouteConstants.PathParam.STUDY_GUID, "{studyGuid}")
                    .replace(RouteConstants.PathParam.USER_GUID, "{userGuid}");
            url = RouteTestUtil.getTestingBaseUrl() + endpoint;
        });
    }

    @AfterClass
    public static void testDataCleanup() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.deleteEnrollmentHook(handle, generatedTestData);
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = null where guid = :guid")
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

    private ValidatableResponse doLookupWithId(String userGuidOrAltPid, int expectedStatusCode) {
        return (
                given().auth().oauth2(dsmClientAccessToken)
                        .pathParam("studyGuid", studyGuid)
                        .pathParam("userGuid", userGuidOrAltPid)
                        .when().get(url)
                        .then().assertThat().statusCode(expectedStatusCode));
    }

    private ValidatableResponse doLookupWithId(String userGuidOrAltPid) {
        return  doLookupWithId(userGuidOrAltPid, 200).contentType(ContentType.JSON)
                .body("dateOfDiagnosis", equalTo("5/1978"))
                .and().body("drawBloodConsent", equalTo(1))
                .and().body("tissueSampleConsent", equalTo(1))
                .and().body("institutions[0].institution", equalTo("Princeton-Plainsboro Teaching Hospital"))
                .and().body("institutions[0].physician", equalTo("House MD"))
                .and().body("institutions[0].streetAddress", equalTo(null))
                .and().body("institutions[0].state", equalTo("New Jersey"))
                .and().body("institutions[0].city", equalTo("West Windsor Township"))
                .and().body("institutions[0].type", equalTo("INSTITUTION"));
    }

    private ValidatableResponse doLookupExpecting404WithId(String userGuidOrAltPid) {
        return doLookupWithId(userGuidOrAltPid, 404);
    }

    @Test
    public void test_providingAltPid_getsUser() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, generatedTestData, false, true);
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("guid", userGuid)
                    .execute());
        });

        try {
            doLookupWithId(legacyAltPid)
                    .and().body("participantId", equalTo(legacyAltPid))
                    .and().body("institutions[0].id", equalTo(TestConstants.TEST_INSTITUTION_LEGACY_GUID));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData);
            });
        }
    }

    @Test
    public void test_providingGuid_getsUser() {
        // The medical provider guid is a random thing! We don't care as long as it's NOT the legacy guid
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, generatedTestData, false, false);
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("guid", userGuid)
                    .execute());
        });

        try {
            doLookupWithId(userGuid)
                    .and().body("participantId", equalTo(TEST_USER_GUID))
                    .and().body("institutions[0].id", not(TestConstants.TEST_INSTITUTION_LEGACY_GUID));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData);
            });
        }
    }

    @Test
    public void test_exited_user_with_release_is_returned() {
        // The medical provider guid is a random thing! We don't care as long as it's NOT the legacy guid
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, generatedTestData, false, false);
            updateUserLegacyAltPid(handle, userGuid, legacyAltPid);
            forceEnrollmentStatusTo(handle, EnrollmentStatusType.EXITED_AFTER_ENROLLMENT);
        });

        try {
            doLookupWithId(userGuid)
                    .and().body("participantId", equalTo(userGuid))
                    .and().body("institutions[0].id", not(TestConstants.TEST_INSTITUTION_LEGACY_GUID));
            doLookupWithId(legacyAltPid)
                    .and().body("participantId", equalTo(legacyAltPid))
                    .and().body("institutions[0].id", not(TestConstants.TEST_INSTITUTION_LEGACY_GUID));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData);
                forceEnrollmentStatusTo(handle, EnrollmentStatusType.ENROLLED);
            });
        }
    }

    private void updateUserLegacyAltPid(Handle handle, String userGuid, String legacyAltPid) {
        assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                .bind("legacyAltPid", legacyAltPid)
                .bind("guid", userGuid)
                .execute());
    }

    private void forceEnrollmentStatusTo(Handle handle, EnrollmentStatusType enrollmentStatusType) {
        JdbiUserStudyEnrollment userStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
        userStudyEnrollment.changeUserStudyEnrollmentStatus(userGuid, studyGuid, enrollmentStatusType);
    }

    @Test
    public void test_exited_user_without_release_returns_404() {
        // The medical provider guid is a random thing! We don't care as long as it's NOT the legacy guid
        AtomicLong activityInstanceId = new AtomicLong();
        AtomicLong operatorId = new AtomicLong();
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, generatedTestData, false, false);
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("guid", userGuid)
                    .execute());
            forceEnrollmentStatusTo(handle, EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT);
            updateUserLegacyAltPid(handle, userGuid, legacyAltPid);
        });

        try {
            doLookupExpecting404WithId(userGuid);
            doLookupExpecting404WithId(legacyAltPid);
        }  finally {
            TransactionWrapper.useTxn(handle -> {
                TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData);
                forceEnrollmentStatusTo(handle, EnrollmentStatusType.ENROLLED);
            });
        }
    }

    private void forceActivityStatusChange(Handle handle, long activityInstanceId, long statusTypeId, long operatorId) {
        assertEquals(1, handle.createUpdate("insert into activity_instance_status (activity_instance_id,"
                                                    + "activity_instance_status_type_id,operator_id,"
                                                    + "updated_at)"
                                                    + " values(:instanceId,:statusTypeId,:operatorId,"
                                                    + ":updatedAt)")
                .bind("instanceId", activityInstanceId)
                .bind("statusTypeId", statusTypeId)
                .bind("operatorId", operatorId)
                .bind("updatedAt", Instant.now().toEpochMilli()).execute());
    }
}
