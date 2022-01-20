package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeleteActivityInstanceRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Set<Long> instanceIdsToDelete = new HashSet<>();
    private static FormActivityDef parentActivity;
    private static FormActivityDef nonDeletableChildAct;
    private static FormActivityDef deletableChildAct;
    private static QuestionDef questionDef;
    private static ActivityInstanceDto parentInstanceDto;
    private static ActivityInstanceDto nonDeletableInstanceDto;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            setupActivityAndInstance(handle);
        });
        String endpoint = RouteConstants.API.USER_ACTIVITIES_INSTANCE
                .replace(RouteConstants.PathParam.USER_GUID, testData.getUserGuid())
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(RouteConstants.PathParam.INSTANCE_GUID, "{instanceGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static void setupActivityAndInstance(Handle handle) {
        String activityCode = "ACT_DELETE" + Instant.now().toEpochMilli();

        questionDef = TextQuestionDef
                .builder(TextInputType.TEXT, activityCode + "_Q", Template.text("question"))
                .build();
        deletableChildAct = FormActivityDef.generalFormBuilder(activityCode + "_CANDELETE", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity with non-deletable instances"))
                .setParentActivityCode(activityCode)
                .setCanDeleteInstances(true)
                .setCanDeleteFirstInstance(false)
                .addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(questionDef))))
                .build();

        nonDeletableChildAct = FormActivityDef.generalFormBuilder(activityCode + "_NONDELETE", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity with non-deletable instances"))
                .setParentActivityCode(activityCode)
                .setCanDeleteInstances(false)
                .build();

        parentActivity = FormActivityDef.generalFormBuilder(activityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "parent activity"))
                .build();

        handle.attach(ActivityDao.class).insertActivity(
                parentActivity, List.of(nonDeletableChildAct, deletableChildAct),
                RevisionMetadata.now(testData.getUserId(), "test"));

        var instanceDao = handle.attach(ActivityInstanceDao.class);
        parentInstanceDto = instanceDao.insertInstance(parentActivity.getActivityId(), testData.getUserGuid());
        nonDeletableInstanceDto = instanceDao.insertInstance(nonDeletableChildAct.getActivityId(),
                testData.getUserGuid(), testData.getUserGuid(), parentInstanceDto.getId());
    }

    @AfterClass
    public static void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            instanceDao.deleteByInstanceGuid(nonDeletableInstanceDto.getGuid());
            instanceDao.deleteByInstanceGuid(parentInstanceDto.getGuid());
        });
    }

    @After
    public void deleteLeftoverInstances() {
        TransactionWrapper.useTxn(handle -> {
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            instanceDao.deleteAllByIds(instanceIdsToDelete);
        });
        instanceIdsToDelete.clear();
    }

    @Test
    public void testDeleteParent_notSupported() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", parentInstanceDto.getGuid())
                .when().delete(url).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("does not allow deleting"));
    }

    @Test
    public void testDeleteNonDeletable_notSupported() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", nonDeletableInstanceDto.getGuid())
                .when().delete(url).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("does not allow deleting"));
    }

    @Test
    public void testDelete_instanceNotFound() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", "foobar")
                .when().delete(url).then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                .body("message", containsString("Could not find activity instance"));
    }

    @Test
    public void testDelete_instanceIsHidden() {
        TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                .attach(ActivityInstanceDao.class)
                .bulkUpdateIsHiddenByActivityIds(
                        testData.getUserId(), true, Set.of(parentActivity.getActivityId()))));
        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", parentInstanceDto.getGuid())
                    .when().delete(url).then().assertThat()
                    .statusCode(404).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                    .body("message", containsString("is hidden"));
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                    .attach(ActivityInstanceDao.class)
                    .bulkUpdateIsHiddenByActivityIds(
                            testData.getUserId(), false, Set.of(parentActivity.getActivityId()))));
        }
    }

    @Test
    public void testDelete_instanceIsHidden_studyAdmin() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateIsHiddenByActivityIds(
                            testData.getUserId(), true, Set.of(parentActivity.getActivityId())));
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
        });
        try {
            // Study admin can access hidden instance, but still should be blocked from deleting parent.
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", parentInstanceDto.getGuid())
                    .when().delete(url).then().assertThat()
                    .statusCode(422).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                assertEquals(1, handle.attach(ActivityInstanceDao.class)
                        .bulkUpdateIsHiddenByActivityIds(
                                testData.getUserId(), false, Set.of(parentActivity.getActivityId())));
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
            });
        }
    }

    @Test
    public void testDelete_success() {
        ActivityInstanceDto firstInstanceDto = TransactionWrapper.withTxn(handle ->
                createInstanceAndDeferCleanup(handle, deletableChildAct.getActivityId()));
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            var instance = createInstanceAndDeferCleanup(handle, deletableChildAct.getActivityId());
            handle.attach(AnswerDao.class).createAnswer(
                    testData.getUserId(), instance.getId(),
                    new TextAnswer(null, questionDef.getStableId(), null, "some value"));
            return instance;
        });

        given().auth().oauth2(token)
                .pathParam("instanceGuid", firstInstanceDto.getGuid())
                .when().delete(url).then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.OPERATION_NOT_ALLOWED))
                .body("message", containsString("first instance"));
        given().auth().oauth2(token)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().delete(url).then().assertThat()
                .statusCode(200);

        TransactionWrapper.useTxn(handle -> {
            var jdbiInstance = handle.attach(JdbiActivityInstance.class);
            assertFalse(jdbiInstance.getByActivityInstanceGuid(instanceDto.getGuid()).isPresent());
            assertTrue("should not delete other instances", jdbiInstance
                    .getByActivityInstanceGuid(parentInstanceDto.getGuid()).isPresent());
            assertTrue("should not delete other instances", jdbiInstance
                    .getByActivityInstanceGuid(nonDeletableInstanceDto.getGuid()).isPresent());
        });
    }

    private ActivityInstanceDto createInstanceAndDeferCleanup(Handle handle, long activityId) {
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        var instanceDto = instanceDao.insertInstance(activityId, testData.getUserGuid(),
                testData.getUserGuid(), parentInstanceDto.getId());
        instanceIdsToDelete.add(instanceDto.getId());
        return instanceDto;
    }
}
