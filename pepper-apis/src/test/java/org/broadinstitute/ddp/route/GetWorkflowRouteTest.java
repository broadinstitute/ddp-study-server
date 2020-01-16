package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.constants.RouteConstants.QueryParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiWorkflowTransition;
import org.broadinstitute.ddp.db.dao.WorkflowDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StaticState;
import org.broadinstitute.ddp.model.workflow.WorkflowTransition;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetWorkflowRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static FormActivityDef fromActivity;
    private static FormActivityDef nextActivity;
    private static long studyId;
    private static String token;
    private static String url;

    private List<String> instanceGuidsToDelete = new ArrayList<>();
    private List<Long> transitionIdsToDelete = new ArrayList<>();

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            studyId = testData.getStudyId();
            fromActivity = insertNewActivity(handle);
            nextActivity = insertNewActivity(handle);
        });
        String endpoint = API.USER_STUDY_WORKFLOW
                .replace(PathParam.USER_GUID, testData.getTestingUser().getUserGuid())
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid());
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static FormActivityDef insertNewActivity(Handle handle) {
        String code = "GET_WORKFLOW_ACT_" + Instant.now().toEpochMilli();
        FormActivityDef form = FormActivityDef.generalFormBuilder(code, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "dummy activity " + code))
                .build();
        handle.attach(ActivityDao.class).insertActivity(form, RevisionMetadata.now(testData.getUserId(), "add " + code));
        assertNotNull(form.getActivityId());
        return form;
    }

    @After
    public void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            for (String instanceGuid : instanceGuidsToDelete) {
                instanceDao.deleteByInstanceGuid(instanceGuid);
            }
            instanceGuidsToDelete.clear();

            JdbiWorkflowTransition jdbiTrans = handle.attach(JdbiWorkflowTransition.class);
            for (long transitionId : transitionIdsToDelete) {
                assertEquals(1, jdbiTrans.deleteById(transitionId));
            }
            transitionIdsToDelete.clear();
        });
    }

    @Test
    public void testGet_requiresFromQueryParam() {
        given().auth().oauth2(token)
                .when().get(url).then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.MISSING_FROM_PARAM))
                .body("message", containsString("is required"));
    }

    @Test
    public void testGet_fromActivityState_requiresParamForActivityInfo() {
        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.ACTIVITY)
                .when().get(url).then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.INVALID_FROM_PARAM))
                .body("message", containsString("requires activity code or instance guid"));
    }

    @Test
    public void testGet_fromActivityState_nonExistingActivity() {
        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.ACTIVITY)
                .queryParam(QueryParam.ACTIVITY_CODE, "ABC1234XYZ")
                .when().get(url).then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.INVALID_FROM_PARAM))
                .body("message", containsString("activity code"))
                .body("message", containsString("does not refer to a valid activity"));
    }

    @Test
    public void testGet_fromActivityState_nonExistingInstance() {
        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.ACTIVITY)
                .queryParam(QueryParam.INSTANCE_GUID, "ABC1234XYZ")
                .when().get(url).then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.INVALID_FROM_PARAM))
                .body("message", containsString("instance guid"))
                .body("message", containsString("does not refer to a valid activity"));
    }

    @Test
    public void testGet_fromActivityState_acceptsActivityCode() {
        TransactionWrapper.useTxn(handle -> {
            ActivityState current = new ActivityState(fromActivity.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, current, StaticState.done(), "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1);
        });

        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.ACTIVITY)
                .queryParam(QueryParam.ACTIVITY_CODE, fromActivity.getActivityCode())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("next", equalTo(StateType.DONE.name()));
    }

    @Test
    public void testGet_fromActivityState_prefersActivityCodeOverInstanceGuid() {
        TransactionWrapper.useTxn(handle -> {
            ActivityState current = new ActivityState(fromActivity.getActivityId());
            ActivityState next = new ActivityState(nextActivity.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, current, next, "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1);
        });

        ActivityInstanceDto nextInstance = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, nextActivity.getActivityId()));

        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.ACTIVITY)
                .queryParam(QueryParam.ACTIVITY_CODE, fromActivity.getActivityCode())
                .queryParam(QueryParam.INSTANCE_GUID, "some-guid-that-is-not-used")
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("next", equalTo(StateType.ACTIVITY.name()))
                .body("activityCode", equalTo(nextActivity.getActivityCode()))
                .body("instanceGuid", equalTo(nextInstance.getGuid()));
    }

    @Test
    public void testGet_fromStaticState() {
        TransactionWrapper.useTxn(handle -> {
            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), StaticState.done(), "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1);
        });

        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.START)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("next", equalTo(StateType.DONE.name()));
    }

    @Test
    public void testGet_fromUnknownState_notSupported() {
        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, "UNKNOWN")
                .when().get(url).then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.INVALID_FROM_PARAM))
                .body("message", containsString("not a valid state"));
    }

    @Test
    public void testGetStart_noStartActivityConfigured_returnsUnknown() {
        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.START)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("next", equalTo("UNKNOWN"));
    }

    @Test
    public void testGetStart_returnsStartActivityWithInstance() {
        TransactionWrapper.useTxn(handle -> {
            ActivityState next = new ActivityState(nextActivity.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), next, "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1);
        });

        ActivityInstanceDto startInstance = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, nextActivity.getActivityId()));

        given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.START)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("next", equalTo(StateType.ACTIVITY.name()))
                .body("activityCode", equalTo(nextActivity.getActivityCode()))
                .body("instanceGuid", equalTo(startInstance.getGuid()));
    }

    @Test
    public void testGetStart_createsInstanceOfStartActivityIfNoneExists() {
        TransactionWrapper.useTxn(handle -> {
            ActivityState next = new ActivityState(nextActivity.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, StaticState.start(), next, "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1);
        });

        String instanceGuid = given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.START)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("next", equalTo(StateType.ACTIVITY.name()))
                .body("activityCode", equalTo(nextActivity.getActivityCode()))
                .body("instanceGuid", not(isEmptyOrNullString()))
                .and().extract().path("instanceGuid");

        instanceGuidsToDelete.add(instanceGuid);
    }

    @Test
    public void testGetStart_createsInstanceOfAnyActivityIfNoneExists() {
        TransactionWrapper.useTxn(handle -> {
            ActivityState current = new ActivityState(fromActivity.getActivityId());
            ActivityState next = new ActivityState(nextActivity.getActivityId());
            WorkflowTransition t1 = new WorkflowTransition(studyId, current, next, "true", 1);
            insertTransitionsAndDeferCleanup(handle, t1);
        });

        ActivityInstanceDto fromInstance = TransactionWrapper.withTxn(handle ->
                insertNewInstanceAndDeferCleanup(handle, fromActivity.getActivityId()));

        String instanceGuid = given().auth().oauth2(token)
                .queryParam(QueryParam.FROM, StateType.ACTIVITY)
                .queryParam(QueryParam.INSTANCE_GUID, fromInstance.getGuid())
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("next", equalTo(StateType.ACTIVITY.name()))
                .body("activityCode", equalTo(nextActivity.getActivityCode()))
                .body("instanceGuid", not(isEmptyOrNullString()))
                .and().extract().path("instanceGuid");

        instanceGuidsToDelete.add(instanceGuid);
    }


    private ActivityInstanceDto insertNewInstanceAndDeferCleanup(Handle handle, long activityId) {
        ActivityInstanceDto dto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityId, testData.getUserGuid());
        instanceGuidsToDelete.add(dto.getGuid());
        return dto;
    }

    private void insertTransitionsAndDeferCleanup(Handle handle, WorkflowTransition... transitions) {
        handle.attach(WorkflowDao.class).insertTransitions(Arrays.asList(transitions));
        Arrays.stream(transitions).forEach(trans -> {
            assertNotNull(trans.getId());
            transitionIdsToDelete.add(trans.getId());
        });
    }
}
