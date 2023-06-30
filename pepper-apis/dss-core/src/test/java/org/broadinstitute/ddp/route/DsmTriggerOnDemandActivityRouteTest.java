package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.constants.RouteConstants.API;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.dsm.TriggerActivityPayload;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DsmTriggerOnDemandActivityRouteTest extends DsmRouteTest {

    private static String url;
    private static FormActivityDef activity;
    private static FormActivityDef somaticResultsActivity;

    private Set<String> instanceGuidsToDelete = new HashSet<>();

    @BeforeClass
    public static void setupRouteTest() {
        String endpoint = API.DSM_ONDEMAND_ACTIVITY
                .replace(PathParam.STUDY_GUID, studyGuid)
                .replace(PathParam.ACTIVITY_CODE, "{activityCode}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
        TransactionWrapper.useTxn(DsmTriggerOnDemandActivityRouteTest::insertActivity);
        TransactionWrapper.useTxn(DsmTriggerOnDemandActivityRouteTest::insertSomaticResultsActivity);
    }

    private static FormActivityDef insertActivity(Handle handle) {
        String code = "DSM_TRIGGER_ONDEMAND_ACTIVITY_TEST" + Instant.now().toEpochMilli();
        activity = FormActivityDef.generalFormBuilder(code, "v1", studyGuid)
                .addName(new Translation("en", "test activity for DsmTriggerOnDemandActivityRoute"))
                .setAllowOndemandTrigger(true)
                .setMaxInstancesPerUser(1)
                .build();
        handle.attach(ActivityDao.class).insertActivity(activity, RevisionMetadata.now(generatedTestData.getUserId(), "test"));
        assertNotNull(activity.getActivityId());
        return activity;
    }

    private static FormActivityDef insertSomaticResultsActivity(Handle handle) {

        var textQ = TextQuestionDef.builder()
                .setStableId(DsmTriggerOnDemandActivityRoute.RESULT_FILE_STABLE_ID)
                .setPrompt(Template.text("results file question"))
                .setInputType(TextInputType.TEXT)
                .build();

        somaticResultsActivity = FormActivityDef.generalFormBuilder(DsmTriggerOnDemandActivityRoute.RESULT_FILE_ACTIVITY_ID,
                        "v1", studyGuid)
                .addName(new Translation("en", "test Somatic Results activity for DsmTriggerOnDemandActivityRoute"))
                .setAllowOndemandTrigger(true)
                .setClosing(new FormSectionDef(null, List.of(new QuestionBlockDef(textQ))))
                .build();
        handle.attach(ActivityDao.class).insertActivity(somaticResultsActivity,
                RevisionMetadata.now(generatedTestData.getUserId(), "test"));
        assertNotNull(somaticResultsActivity.getActivityId());
        return somaticResultsActivity;
    }

    @AfterClass
    public static void testDataCleanup() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = null where guid = :guid")
                    .bind("guid", userGuid)
                    .execute());
        });
    }

    @After
    public void deleteInstances() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            for (String guid : instanceGuidsToDelete) {
                instanceDao.deleteByInstanceGuid(guid);
            }
        });
        instanceGuidsToDelete.clear();
    }

    @Test
    public void test_noPayload() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", allOf(containsString("payload"), containsString("none was found")));
    }

    @Test
    public void test_missingParticipantGuid() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .body(new TriggerActivityPayload(null, 1L), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", allOf(containsString("participantId"), containsString("not be blank")));
    }

    @Test
    public void test_blankParticipantGuid() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .body(new TriggerActivityPayload(" \t\n", 1L), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", allOf(containsString("participantId"), containsString("not be blank")));
    }

    @Test
    public void test_missingTriggerId() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .body(new TriggerActivityPayload("abc", null), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", allOf(containsString("triggerId"), containsString("not be null")));
    }

    @Test
    public void test_noExpectedProperties() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .body("{ \"a\": \"b\" }")
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", allOf(containsString("participantId"), containsString("triggerId")));
    }

    @Test
    public void test_nonExistentActivity() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", "not-a-real-activity-code")
                .body(new TriggerActivityPayload("a", 1L), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                .body("message", containsString("activity"));
    }

    @Test
    public void test_nonExistentUser() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .body(new TriggerActivityPayload("a", 1L), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.USER_NOT_FOUND))
                .body("message", containsString("participant"));
    }

    @Test
    public void test_successfulTrigger() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .body(new TriggerActivityPayload(userGuid, 123L), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON);

        TransactionWrapper.useTxn(handle -> {
            List<ActivityInstanceDto> dtos = handle.attach(JdbiActivityInstance.class)
                    .findAllByUserGuidAndActivityCode(userGuid, activity.getActivityCode(), generatedTestData.getStudyId());
            assertNotNull(dtos);
            assertEquals(1, dtos.size());

            ActivityInstanceDto instanceDto = dtos.get(0);
            assertEquals((Long) 123L, instanceDto.getOnDemandTriggerId());
            instanceGuidsToDelete.add(instanceDto.getGuid());
        });
    }

    @Test
    public void test_successfulTriggerAltPid() {
        String legacyAltPid = "GUID.GUID.GUID.12345";
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("guid", userGuid)
                    .execute());
        });

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .body(new TriggerActivityPayload(legacyAltPid, 123L), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON);

        TransactionWrapper.useTxn(handle -> {
            List<ActivityInstanceDto> dtos = handle.attach(JdbiActivityInstance.class)
                    .findAllByUserGuidAndActivityCode(userGuid, activity.getActivityCode(), generatedTestData.getStudyId());
            assertNotNull(dtos);
            assertEquals(1, dtos.size());

            ActivityInstanceDto instanceDto = dtos.get(0);
            assertEquals((Long) 123L, instanceDto.getOnDemandTriggerId());
            instanceGuidsToDelete.add(instanceDto.getGuid());
        });
    }

    @Test
    public void test_reachedMaximumInstances_disallowTriggering() {
        TransactionWrapper.useTxn(handle -> insertInstanceAndDeferCleanup(handle, 456L));

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .body(new TriggerActivityPayload(userGuid, 123L), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(500).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.TOO_MANY_INSTANCES))
                .body("message", containsString("maximum"));
    }

    @Test
    public void test_invalidFilePath_failTriggering() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", DsmTriggerOnDemandActivityRoute.RESULT_FILE_ACTIVITY_ID)
                .body(new TriggerActivityPayload(userGuid, 9876L), ObjectMapperType.GSON) //no results file path passed
                .when().post(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ANSWER_NOT_FOUND))
                .body("message", containsString("Invalid results file path"));
    }

    @Test
    public void test_successfulSomaticResultsTrigger() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", somaticResultsActivity.getActivityCode())
                .body(new TriggerActivityPayload(userGuid, 9876L, "testSomaticResultsFile.pdf"), ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON);

        TransactionWrapper.useTxn(handle -> {
            List<ActivityInstanceDto> dtos = handle.attach(JdbiActivityInstance.class)
                    .findAllByUserGuidAndActivityCode(userGuid, somaticResultsActivity.getActivityCode(), generatedTestData.getStudyId());
            assertNotNull(dtos);
            assertEquals(1, dtos.size());

            ActivityInstanceDto instanceDto = dtos.get(0);
            assertEquals((Long) 9876L, instanceDto.getOnDemandTriggerId());

            //query Answer and assert
            AnswerDao answerDao = handle.attach(AnswerDao.class);
            Answer answer = answerDao.findAnswerByInstanceGuidAndQuestionStableId(instanceDto.getGuid(),
                    DsmTriggerOnDemandActivityRoute.RESULT_FILE_STABLE_ID).get();
            assertNotNull(answer.getAnswerGuid());
            assertEquals("testSomaticResultsFile.pdf", ((TextAnswer)answer).getValue());
            answerDao.deleteAnswer(answer.getAnswerId());

            instanceGuidsToDelete.add(instanceDto.getGuid());
        });
    }

    private ActivityInstanceDto insertInstanceAndDeferCleanup(Handle handle, Long triggerId) {
        long nowMillis = Instant.now().toEpochMilli();
        ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activity.getActivityId(), userGuid, userGuid, CREATED, false, nowMillis, triggerId, null);
        assertNotNull(instanceDto);
        assertNotNull(instanceDto.getGuid());
        instanceGuidsToDelete.add(instanceDto.getGuid());
        return instanceDto;
    }
}
