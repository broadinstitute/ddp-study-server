package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;

import java.time.Instant;
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
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetActivityInstanceSummaryRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static FormActivityDef parentActivity;
    private static FormActivityDef nestedActivity;
    private static ActivityInstanceDto parentInstanceDto;
    private static ActivityInstanceDto nestedInstanceDto;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            setupActivityAndInstance(handle);
        });
        String endpoint = RouteConstants.API.USER_ACTIVITY_SUMMARY
                .replace(RouteConstants.PathParam.USER_GUID, testData.getUserGuid())
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(RouteConstants.PathParam.INSTANCE_GUID, "{instanceGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static void setupActivityAndInstance(Handle handle) {
        String activityCode = "ACT_SUMMARY" + Instant.now().toEpochMilli();
        nestedActivity = FormActivityDef.generalFormBuilder(activityCode + "_NESTED", "v1", testData.getStudyGuid())
                .addName(new Translation("en", "nested activity"))
                .setParentActivityCode(activityCode)
                .setCanDeleteInstances(true)
                .build();
        TextQuestionDef textQuestion = TextQuestionDef
                .builder(TextInputType.TEXT, activityCode + "_Q1", Template.text("q1"))
                .build();
        parentActivity = FormActivityDef.generalFormBuilder(activityCode, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "parent activity"))
                .addSubtitle(new Translation("en", "$ddp.answer(\"" + textQuestion.getStableId() + "\",\"fallback\")"))
                .addSection(new FormSectionDef(null, List.of(new QuestionBlockDef(textQuestion))))
                .build();
        handle.attach(ActivityDao.class).insertActivity(
                parentActivity, List.of(nestedActivity),
                RevisionMetadata.now(testData.getUserId(), "test"));

        var instanceDao = handle.attach(ActivityInstanceDao.class);
        parentInstanceDto = instanceDao.insertInstance(parentActivity.getActivityId(), testData.getUserGuid());
        nestedInstanceDto = instanceDao.insertInstance(nestedActivity.getActivityId(), testData.getUserGuid(),
                testData.getUserGuid(), parentInstanceDto.getId());

        var answerDao = handle.attach(AnswerDao.class);
        answerDao.createAnswer(testData.getUserId(), parentInstanceDto.getId(),
                new TextAnswer(null, textQuestion.getStableId(), null, "the answer!"));
    }

    @AfterClass
    public static void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            var answerDao = handle.attach(AnswerDao.class);
            answerDao.deleteAllByInstanceIds(Set.of(parentInstanceDto.getId()));
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            instanceDao.deleteByInstanceGuid(nestedInstanceDto.getGuid());
            instanceDao.deleteByInstanceGuid(parentInstanceDto.getGuid());
        });
    }

    @Test
    public void testSuccessful() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", parentInstanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("activityCode", equalTo(parentActivity.getActivityCode()))
                .body("activitySubtitle", equalTo("the answer!"))
                .body("instanceGuid", equalTo(parentInstanceDto.getGuid()))
                .body("parentInstanceGuid", nullValue())
                .body("canDelete", equalTo(false));
    }

    @Test
    public void testSuccessful_childInstanceSummary() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", nestedInstanceDto.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("activityCode", equalTo(nestedActivity.getActivityCode()))
                .body("instanceGuid", equalTo(nestedInstanceDto.getGuid()))
                .body("parentInstanceGuid", equalTo(parentInstanceDto.getGuid()))
                .body("canDelete", equalTo(true));
    }

    @Test
    public void testInstanceNotFound() {
        given().auth().oauth2(token)
                .pathParam("instanceGuid", "foobar")
                .when().get(url).then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                .body("message", containsString("Could not find"));
    }

    @Test
    public void testInstanceIsHidden() {
        TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                .attach(ActivityInstanceDao.class)
                .bulkUpdateIsHiddenByActivityIds(
                        testData.getUserId(), true, Set.of(parentActivity.getActivityId()))));
        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", parentInstanceDto.getGuid())
                    .when().get(url).then().assertThat()
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
    public void testMultipleInstancesOfActivity() {
        ActivityInstanceDto anotherInstanceDto = TransactionWrapper.withTxn(handle -> handle
                .attach(ActivityInstanceDao.class)
                .insertInstance(nestedActivity.getActivityId(), testData.getUserGuid(),
                        testData.getUserGuid(), parentInstanceDto.getId()));
        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", anotherInstanceDto.getGuid())
                    .when().get(url).then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("activityCode", equalTo(nestedActivity.getActivityCode()))
                    .body("instanceGuid", equalTo(anotherInstanceDto.getGuid()))
                    .body("parentInstanceGuid", equalTo(parentInstanceDto.getGuid()))
                    .body("activityName", containsString("#2"));
        } finally {
            TransactionWrapper.useTxn(handle -> handle
                    .attach(ActivityInstanceDao.class)
                    .deleteByInstanceGuid(anotherInstanceDto.getGuid()));
        }
    }

    @Test
    public void testStudyAdmin_instanceIsHidden_stillReturned() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.attach(ActivityInstanceDao.class)
                    .bulkUpdateIsHiddenByActivityIds(
                            testData.getUserId(), true, Set.of(parentActivity.getActivityId())));
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
        });
        try {
            given().auth().oauth2(token)
                    .pathParam("instanceGuid", parentInstanceDto.getGuid())
                    .when().get(url).then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("instanceGuid", equalTo(parentInstanceDto.getGuid()))
                    .body("isHidden", equalTo(true));
        } finally {
            TransactionWrapper.useTxn(handle -> {
                assertEquals(1, handle.attach(ActivityInstanceDao.class)
                        .bulkUpdateIsHiddenByActivityIds(
                                testData.getUserId(), false, Set.of(parentActivity.getActivityId())));
                handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
            });
        }
    }
}
