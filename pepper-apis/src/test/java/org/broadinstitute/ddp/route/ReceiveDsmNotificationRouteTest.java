package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType.SALIVA_RECEIVED;
import static org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType.TESTBOSTON_RECEIVED;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.UserSql;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.json.dsm.DsmNotificationPayload;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReceiveDsmNotificationRouteTest extends DsmRouteTest {

    private static final String SENDGRID_TEST_TEMPLATE = "e14a7315-fc82-4c66-bea9-5071d68aee2f";
    private static final String LEGACY_ALT_PID = "12345.GUID-GUID-GUID";
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static long eventConfigId;
    private static String urlTemplate;

    @BeforeClass
    public static void setup() {
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + API.DSM_NOTIFICATION
                .replace(PathParam.STUDY_GUID, "{study}")
                .replace(PathParam.USER_GUID, "{user}");
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            eventConfigId = generateDsmNotificationTestEventConfiguration(
                    handle, testData, SALIVA_RECEIVED, SENDGRID_TEST_TEMPLATE);
        });
    }

    private static long generateDsmNotificationTestEventConfiguration(
            Handle handle,
            TestDataSetupUtil.GeneratedTestData generatedTestData,
            DsmNotificationEventType dsmEventType,
            String sendgridTemplateGuid
    ) {
        long triggerId = handle.attach(EventTriggerDao.class)
                .insertDsmNotificationTrigger(dsmEventType);
        long actionId = handle.attach(EventActionDao.class).insertNotificationAction(
                new SendgridEmailEventActionDto(sendgridTemplateGuid, "en", false));
        return handle.attach(JdbiEventConfiguration.class).insert(
                triggerId, actionId, generatedTestData.getStudyId(),
                Instant.now().toEpochMilli(), 1, null, null, null, true, 1);
    }

    @Before
    public void insertTestData() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.setUserEnrollmentStatus(handle, testData, EnrollmentStatusType.ENROLLED);
            assertEquals(1, handle.attach(UserSql.class).updateLegacyAltPidById(testData.getUserId(), LEGACY_ALT_PID));
        });
    }

    @After
    public void deleteTestData() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiEventConfigurationOccurrenceCounter.class).deleteById(eventConfigId, testData.getUserId());
            handle.attach(QueuedEventDao.class).deleteQueuedEventsByEventConfigurationId(eventConfigId);
            assertEquals(1, handle.attach(UserSql.class).updateLegacyAltPidById(testData.getUserId(), null));
            TestDataSetupUtil.deleteEnrollmentStatus(handle, testData);
        });
    }

    @Test
    public void testStudyNotFound() {
        var payload = new DsmNotificationPayload(null, SALIVA_RECEIVED.name(), 1L);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", "foobar")
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.STUDY_NOT_FOUND));
    }

    @Test
    public void testUserNotFound() {
        var payload = new DsmNotificationPayload(null, SALIVA_RECEIVED.name(), 1L);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", "foobar")
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.USER_NOT_FOUND));
    }

    @Test
    public void testNotificationEventTypeUnknown() {
        var payload = new DsmNotificationPayload(null, "foobar", 1L);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_FOUND))
                .body("message", containsString("event type 'foobar'"));
    }

    @Test
    public void testEvents_success() {
        var payload = new DsmNotificationPayload(null, SALIVA_RECEIVED.name(), 1L);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(200);
        assertTrue("events should have ran and queued notification", checkIfNotificationQueued());
    }

    @Test
    public void testEvents_usingLegacyAltPid_success() {
        var payload = new DsmNotificationPayload(null, SALIVA_RECEIVED.name(), 1L);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", LEGACY_ALT_PID)
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(200);
        assertTrue("events should have ran and queued notification", checkIfNotificationQueued());
    }

    @Test
    public void testEvents_mismatchedDsmEventType() {
        var payload = new DsmNotificationPayload(null, TESTBOSTON_RECEIVED.name(), 1L);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", LEGACY_ALT_PID)
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(200);
        assertFalse("events should not have ran", checkIfNotificationQueued());
    }

    private boolean checkIfNotificationQueued() {
        return TransactionWrapper.withTxn(handle -> {
            var eventDao = handle.attach(EventDao.class);
            boolean foundQueuedTemplate = false;
            for (QueuedEventDto pendingEvent : eventDao.findAllQueuedEvents()) {
                if (pendingEvent instanceof QueuedNotificationDto) {
                    QueuedNotificationDto notificationDto = (QueuedNotificationDto) pendingEvent;
                    foundQueuedTemplate = eventDao
                            .getNotificationTemplatesForEvent(notificationDto.getEventConfigurationId())
                            .stream()
                            .anyMatch(tmpl -> tmpl.getTemplateKey().equals(SENDGRID_TEST_TEMPLATE));
                    if (foundQueuedTemplate) {
                        break;
                    }
                }
            }
            return foundQueuedTemplate;
        });
    }
}
