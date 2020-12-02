package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.model.dsm.DsmNotificationEventType.SALIVA_RECEIVED;
import static org.broadinstitute.ddp.model.dsm.DsmNotificationEventType.TESTBOSTON_RECEIVED;
import static org.broadinstitute.ddp.model.dsm.DsmNotificationEventType.TESTBOSTON_SENT;
import static org.broadinstitute.ddp.model.dsm.DsmNotificationEventType.TEST_RESULT;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.I18nTemplateConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitScheduleDao;
import org.broadinstitute.ddp.db.dao.KitScheduleSql;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.UserSql;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.dsm.DsmNotificationPayload;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.dsm.KitReasonType;
import org.broadinstitute.ddp.model.dsm.TestResult;
import org.broadinstitute.ddp.model.pex.Expression;
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
        var payload = new DsmNotificationPayload(SALIVA_RECEIVED.name());
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
        var payload = new DsmNotificationPayload(SALIVA_RECEIVED.name());
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
        var payload = new DsmNotificationPayload("foobar");
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
        var payload = new DsmNotificationPayload(TEST_RESULT.name());
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
        var payload = new DsmNotificationPayload(TEST_RESULT.name());
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
        var payload = new DsmNotificationPayload(TEST_RESULT.name());
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
        var payload = new DsmNotificationPayload(SALIVA_RECEIVED.name());
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
        var payload = new DsmNotificationPayload(SALIVA_RECEIVED.name());
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
        var payload = new DsmNotificationPayload(TESTBOSTON_RECEIVED.name());
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
    public void testEvents_testResult_replacementKit() {
        var activity = TransactionWrapper.withTxn(handle -> {
            var form = FormActivityDef.generalFormBuilder("ACT" + System.currentTimeMillis(), "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "dummy activity"))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
            return form;
        });
        TransactionWrapper.useTxn(handle -> {
            long triggerId = handle.attach(EventTriggerDao.class).insertDsmNotificationTrigger(TEST_RESULT);
            long actionId = handle.attach(EventActionDao.class)
                    .insertInstanceCreationAction(activity.getActivityId());
            Expression cancelExpr = handle.attach(JdbiExpression.class).insertExpression(
                    "!user.event.kit.isReason(\"REPLACEMENT\")");
            handle.attach(JdbiEventConfiguration.class).insert(
                    triggerId, actionId, testData.getStudyId(),
                    Instant.now().toEpochMilli(), 1, null, null, cancelExpr.getId(), false, 1);
        });

        // This DSM event should be ignored because it's not replacement.
        var payload = new DsmNotificationPayload(TEST_RESULT.name(), KitReasonType.NORMAL);
        var result = new TestResult("NEGATIVE", Instant.now(), false);
        payload.setEventData(GsonUtil.standardGson().toJsonTree(result));
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(200);

        // This DSM event should pass because it is replacement.
        payload = new DsmNotificationPayload(TEST_RESULT.name(), KitReasonType.REPLACEMENT);
        var result2 = new TestResult("Positive", Instant.now(), false);
        payload.setEventData(GsonUtil.standardGson().toJsonTree(result2));
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(200);

        TransactionWrapper.useTxn(handle -> {
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            List<ActivityResponse> instances = instanceDao.findBaseResponsesByStudyAndUserIds(
                    testData.getStudyId(), Set.of(testData.getUserId()), true, Set.of(activity.getActivityId()))
                    .collect(Collectors.toList());
            assertEquals("should only run event once", 1, instances.size());

            ActivityResponse instance = instances.get(0);
            Map<String, String> substitutions = instanceDao.findSubstitutions(instance.getId());
            assertFalse(substitutions.isEmpty());
            assertEquals("should be result of second request and should normalize result code",
                    "POSITIVE", substitutions.get(I18nTemplateConstants.Snapshot.TEST_RESULT_CODE));
            assertEquals(result2.getTimeCompleted().toString(),
                    substitutions.get(I18nTemplateConstants.Snapshot.TEST_RESULT_TIME_COMPLETED));
        });
    }

    @Test
    public void testEvents_setInitialKitSentTime() {
        StudyDto study = TransactionWrapper.withTxn(handle ->
                TestDataSetupUtil.generateTestStudy(handle, RouteTestUtil.getConfig()));

        var kitConfigId = new AtomicLong();
        long recordId = TransactionWrapper.withTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                    testData.getUserId(), study.getId(), EnrollmentStatusType.ENROLLED);

            long kitTypeId = handle.attach(KitTypeDao.class).getTestBostonKitType().getId();
            long configId = handle.attach(KitConfigurationDao.class)
                    .insertConfiguration(study.getId(), 1L, kitTypeId, false);
            kitConfigId.set(configId);

            return handle.attach(KitScheduleDao.class)
                    .createScheduleRecord(testData.getUserId(), configId);
        });

        var payload = new DsmNotificationPayload(TESTBOSTON_SENT.name());
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("study", study.getGuid())
                .pathParam("user", testData.getUserGuid())
                .body(payload, ObjectMapperType.GSON)
                .log().all()
                .when().post(urlTemplate)
                .then().assertThat()
                .log().all()
                .statusCode(200);

        try {
            TransactionWrapper.useTxn(handle -> {
                var record = handle.attach(KitScheduleDao.class).findRecord(recordId).orElse(null);
                assertNotNull(record);
                assertNotNull(record.getInitialKitSentTime());
            });
        } finally {
            TransactionWrapper.useTxn(handle -> {
                DBUtils.checkDelete(1, handle.attach(KitScheduleSql.class).deleteRecord(recordId));
                DBUtils.checkDelete(1, handle.attach(KitConfigurationDao.class).deleteConfiguration(kitConfigId.get()));
            });
        }
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
