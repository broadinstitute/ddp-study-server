package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiQueuedEvent;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.json.study.StudyExitRequestPayload;
import org.broadinstitute.ddp.model.study.StudyExitRequest;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SendExitNotificationRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String urlTemplate;
    private static String url;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        token = testData.getTestingUser().getToken();

        String endpoint = RouteConstants.API.USER_STUDY_EXIT
                .replace(RouteConstants.PathParam.USER_GUID, "{user}")
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}");
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + endpoint;
        url = urlTemplate
                .replace("{user}", testData.getUserGuid())
                .replace("{study}", testData.getStudyGuid());
    }

    @After
    public void clearData() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).deleteByUserGuidStudyGuid(testData.getUserGuid(), testData.getStudyGuid());
            handle.attach(StudyDao.class).deleteExitRequest(testData.getUserId());
        });
    }

    @Test
    public void test_unauthorized_401() {
        given().when().post(url)
                .then().assertThat()
                .statusCode(401);
    }

    @Test
    public void test_invalidUser_401() {
        given().auth().oauth2(token)
                .pathParam("user", "not-found")
                .pathParam("study", testData.getStudyGuid())
                .body(new StudyExitRequestPayload("notes"))
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(401);
    }

    @Test
    public void test_invalidStudy_401() {
        given().auth().oauth2(token)
                .pathParam("user", testData.getUserGuid())
                .pathParam("study", "not-found")
                .body(new StudyExitRequestPayload("notes"))
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(401);
    }

    @Test
    public void test_noPayload_400() {
        given().auth().oauth2(token)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void test_userNotInStudy_422() {
        given().auth().oauth2(token)
                .body(new StudyExitRequestPayload("notes"))
                .when().post(url)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("not in study"));
    }

    @Test
    public void test_userAlreadyExited_422() {
        TransactionWrapper.useTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                testData.getUserGuid(), testData.getStudyGuid(), EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT));

        given().auth().oauth2(token)
                .body(new StudyExitRequestPayload("notes"))
                .when().post(url)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("already exited"));
    }

    @Test
    public void test_userAlreadyMadeExitRequest_422() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(testData.getUserGuid(), testData.getStudyGuid(), EnrollmentStatusType.REGISTERED);
            handle.attach(StudyDao.class).insertExitRequest(new StudyExitRequest(testData.getStudyId(), testData.getUserId(), "notes"));
        });

        given().auth().oauth2(token)
                .body(new StudyExitRequestPayload("notes"))
                .when().post(url)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("already made exit request"));
    }

    @Test
    public void test_studyDoesNotSupportExitRequests_422() {
        TransactionWrapper.useTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                .changeUserStudyEnrollmentStatus(testData.getUserGuid(), testData.getStudyGuid(), EnrollmentStatusType.REGISTERED));

        given().auth().oauth2(token)
                .body(new StudyExitRequestPayload("notes"))
                .when().post(url)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_SUPPORTED))
                .body("message", containsString("does not support"));
    }

    @Test
    public void testCanExitFromSuspendedState() {
        long eventId = TransactionWrapper.withTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                    testData.getUserGuid(), testData.getStudyGuid(), EnrollmentStatusType.CONSENT_SUSPENDED);
            return setupStudyExitConfiguration(handle);
        });

        try {
            postExitAndVerifySuccess();
        } finally {
            TransactionWrapper.useTxn(handle -> deleteStudyExitConfiguration(handle, eventId));
        }
    }

    private void postExitAndVerifySuccess() {
        given().auth().oauth2(token)
                .body(new StudyExitRequestPayload("some notes"))
                .when().post(url)
                .then().assertThat()
                .statusCode(204);
    }

    private long setupStudyExitConfiguration(Handle handle) {
        long triggerId = handle.attach(EventTriggerDao.class).insertExitRequestTrigger();
        long actionId = handle.attach(EventActionDao.class).insertStudyNotificationAction(
                new SendgridEmailEventActionDto("template", "en"));

        return handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, testData.getStudyId(),
                Instant.now().toEpochMilli(), null, null, null, null, true, 1);

    }

    private void deleteStudyExitConfiguration(Handle handle, long eventId) {
        handle.attach(QueuedEventDao.class).deleteQueuedEventsByEventConfigurationId(eventId);
        handle.attach(JdbiEventConfiguration.class).deleteById(eventId);
    }


    @Test
    public void test_exitRequestSuccess_204() {
        long eventId = TransactionWrapper.withTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                    testData.getUserGuid(), testData.getStudyGuid(), EnrollmentStatusType.REGISTERED);

            return setupStudyExitConfiguration(handle);
        });

        try {
            postExitAndVerifySuccess();
            TransactionWrapper.useTxn(handle -> {
                List<Long> res = handle.attach(JdbiQueuedEvent.class).findQueuedEventIdsByEventConfigurationId(eventId);
                assertEquals(1, res.size());

                long queuedId = res.get(0);
                List<NotificationTemplateSubstitutionDto> subs = handle.attach(EventDao.class)
                        .getTemplateSubstitutionsForQueuedNotification(queuedId);
                assertFalse(subs.isEmpty());
                assertTrue(subs.stream().anyMatch(sub ->
                        NotificationTemplateVariables.DDP_PARTICIPANT_EXIT_NOTES.equals(sub.getVariableName())
                                && "some notes".equals(sub.getValue())));

                StudyExitRequest req = handle.attach(StudyDao.class).findExitRequestForUser(testData.getUserId()).orElse(null);
                assertNotNull(req);
                assertEquals("some notes", req.getNotes());
            });
        } finally {
            TransactionWrapper.useTxn(handle -> {
                deleteStudyExitConfiguration(handle, eventId);
            });
        }
    }
}
