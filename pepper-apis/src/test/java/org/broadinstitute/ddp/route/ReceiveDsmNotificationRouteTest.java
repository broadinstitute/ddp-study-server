package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType.SALIVA_RECEIVED;
import static org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType.TESTBOSTON_RECEIVED;
import static org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType.TEST_RESULT;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
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
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.model.dsm.TestResult;
import org.broadinstitute.ddp.model.dsm.TestResultEventType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.GsonUtil;
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
    public void testNotificationEventTestResultMissingEventData() {
        var payload = new DsmNotificationPayload(null, TEST_RESULT.name(), 1L);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("Missing test result"));
    }

    @Test
    public void testNotificationEventTestResultBadEventData() {
        var payload = new DsmNotificationPayload(null, TEST_RESULT.name(), 1L);
        var result = new JsonObject();
        result.addProperty("result", "NEGATIVE");
        result.addProperty("reason", "the reason");
        result.addProperty("timeCompleted", "faulty timestamp");
        payload.setEventData(result);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("parsing event data"));
    }

    @Test
    public void testNotificationEventTestResultInvalidEventData() {
        var payload = new DsmNotificationPayload(null, TEST_RESULT.name(), 1L);
        var result = new JsonObject();
        result.addProperty("result", "NEGATIVE");
        result.addProperty("reason", "the reason");
        payload.setEventData(result);
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .log().all()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("'timeCompleted' must not be null"));
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

    @Test
    public void testEvents_testResult() {
        var activity = TransactionWrapper.withTxn(handle -> {
            var form = FormActivityDef.generalFormBuilder("ACT" + System.currentTimeMillis(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "dummy activity"))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            return form;
        });
        TransactionWrapper.useTxn(handle -> {
            long triggerId = handle.attach(EventTriggerDao.class)
                    .insertDsmNotificationTestResultTrigger(TestResultEventType.ANY);
            long actionId = handle.attach(EventActionDao.class)
                    .insertInstanceCreationAction(activity.getActivityId());
            handle.attach(JdbiEventConfiguration.class).insert(
                    triggerId, actionId, testData.getStudyId(),
                    Instant.now().toEpochMilli(), 1, null, null, null, false, 1);
        });

        var payload = new DsmNotificationPayload(null, TEST_RESULT.name(), 1L);
        var result = new TestResult("NEGATIVE", Instant.now());
        payload.setEventData(GsonUtil.standardGson().toJsonTree(result));
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .log().all()
                .when().post(urlTemplate)
                .then().assertThat()
                .log().all()
                .statusCode(200);

        TransactionWrapper.useTxn(handle -> {
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            List<ActivityResponse> instances = instanceDao.findBaseResponsesByStudyAndUserIds(
                    testData.getStudyId(), Set.of(testData.getUserId()), true, Set.of(activity.getActivityId()))
                    .collect(Collectors.toList());
            assertEquals(1, instances.size());

            ActivityResponse instance = instances.get(0);
            Map<String, String> substitutions = instanceDao.findSubstitutions(instance.getId());
            assertFalse(substitutions.isEmpty());
            assertEquals("NEGATIVE", substitutions.get(I18nTemplateConstants.Snapshot.TEST_RESULT_CODE));
            assertEquals(result.getTimeCompleted().toString(),
                    substitutions.get(I18nTemplateConstants.Snapshot.TEST_RESULT_TIME_COMPLETED));
        });
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
