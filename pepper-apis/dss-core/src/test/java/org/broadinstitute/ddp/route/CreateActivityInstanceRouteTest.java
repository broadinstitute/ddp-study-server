package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.ActivityInstanceCreationPayload;
import org.broadinstitute.ddp.json.ActivityInstanceCreationResponse;
import org.broadinstitute.ddp.json.form.BlockVisibility;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateActivityInstanceRouteTest extends IntegrationTestSuite.TestCase {

    private static final Collection<String> activityInstancesToDelete = ConcurrentHashMap.newKeySet();
    private static final Collection<String> parentInstancesToDelete = new HashSet<>();
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static TestFormActivity anotherActivity;
    private static FormActivityDef parentActivity;
    private static FormActivityDef nestedActivity;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            String endpoint = RouteConstants.API.USER_ACTIVITIES
                    .replace(RouteConstants.PathParam.USER_GUID, testData.getUserGuid())
                    .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
            url = RouteTestUtil.getTestingBaseUrl() + endpoint;
            token = testData.getTestingUser().getToken();

            anotherActivity = TestFormActivity.builder()
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            String activityCode = "ACT" + Instant.now().toEpochMilli();
            nestedActivity = FormActivityDef.generalFormBuilder(activityCode + "_NESTED", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "nested activity"))
                    .setParentActivityCode(activityCode)
                    .build();

            QuestionBlockDef questionBlockDef = new QuestionBlockDef(
                    TextQuestionDef.builder(TextInputType.TEXT, "sid-text", Template.text("")).build());
            questionBlockDef.setShownExpr("true");
            parentActivity = FormActivityDef.generalFormBuilder(activityCode, "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "parent activity"))
                    .setMaxInstancesPerUser(1)
                    .addSection(new FormSectionDef(null, Arrays.asList(questionBlockDef)))
                    .setHideInstances(true)
                    .build();
            handle.attach(ActivityDao.class).insertActivity(
                    parentActivity, List.of(nestedActivity),
                    RevisionMetadata.now(testData.getUserId(), "test"));
        });
    }

    @After
    public void deleteCreatedInstances() {
        TransactionWrapper.useTxn(handle -> {
            // Delete child instances before parent instances.
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            var instanceGuids = new ArrayList<>(activityInstancesToDelete);
            instanceGuids.addAll(parentInstancesToDelete);
            for (var instanceGuid : instanceGuids) {
                instanceDao.deleteByInstanceGuid(instanceGuid);
            }
        });
        activityInstancesToDelete.clear();
        parentInstancesToDelete.clear();
    }

    private ValidatableResponse makeCreationRequest(String activityCode) {
        var payload = new ActivityInstanceCreationPayload(activityCode);
        return given().auth().oauth2(token)
                .body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat();
    }

    private ValidatableResponse makeChildCreationRequest(String activityCode, String parentInstanceGuid) {
        var payload = new ActivityInstanceCreationPayload(activityCode, parentInstanceGuid);
        return given().auth().oauth2(token)
                .body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat();
    }

    @Test
    public void testInvalidJson() {
        given().auth().oauth2(token)
                .body("wicked bad payload")
                .when().post(url)
                .then().assertThat()
                .statusCode(400);
    }

    @Test
    public void testMissingActivityCode() {
        makeCreationRequest(null).statusCode(400);
    }

    @Test
    public void testUnknownActivityCode() {
        makeCreationRequest("foobar")
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND));
    }

    @Test
    public void testCreateActivityNoExistingInstancesShouldSucceed() {
        String instanceGuid = makeCreationRequest(parentActivity.getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");

        assertTrue(StringUtils.isNotBlank(instanceGuid));
        activityInstancesToDelete.add(instanceGuid);

        ActivityInstanceDto instance = TransactionWrapper.withTxn(handle -> handle
                .attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(instanceGuid)
                .orElse(null));
        assertNotNull(instance);
        assertNull("should not have a parent", instance.getParentInstanceGuid());
    }

    @Test
    public void testCreateActivityShouldFailBecauseTooManyInstances() {
        String instanceGuid = makeCreationRequest(parentActivity.getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");

        assertTrue(StringUtils.isNoneBlank(instanceGuid));
        activityInstancesToDelete.add(instanceGuid);

        makeCreationRequest(parentActivity.getActivityCode())
                .statusCode(422)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.TOO_MANY_INSTANCES));
    }

    @Test
    public void testCreateActivityShouldHideExistingInstances() {
        try {
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                    .attach(JdbiActivity.class)
                    .updateMaxInstancesPerUserById(parentActivity.getActivityId(), null)));

            String firstInstanceGuid = makeCreationRequest(parentActivity.getActivityCode())
                    .statusCode(200).and().extract().path("instanceGuid");

            assertTrue(StringUtils.isNoneBlank(firstInstanceGuid));
            activityInstancesToDelete.add(firstInstanceGuid);

            String secondInstanceGuid = makeCreationRequest(parentActivity.getActivityCode())
                    .statusCode(200).and().extract().path("instanceGuid");

            assertTrue(StringUtils.isNoneBlank(secondInstanceGuid));
            activityInstancesToDelete.add(secondInstanceGuid);


            ActivityInstanceDto firstInstance = TransactionWrapper.withTxn(handle -> handle
                    .attach(JdbiActivityInstance.class)
                    .getByActivityInstanceGuid(firstInstanceGuid)
                    .orElse(null));
            assertNotNull(firstInstance);
            assertTrue(firstInstance.isHidden());
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                    .attach(JdbiActivity.class)
                    .updateMaxInstancesPerUserById(parentActivity.getActivityId(), 1)));
        }
    }

    @Test
    public void runCreateInstanceConcurrently() {
        try {
            // set constraint that only 1 activity instance of test activity is allowed
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                    .attach(JdbiActivity.class)
                    .updateMaxInstancesPerUserById(parentActivity.getActivityId(), 1)));
            Gson gson = GsonUtil.standardGson();
            // parallelize requests to test concurrent handling of this situation
            List<Integer> returnCodes = IntStream.range(0, 4).parallel().mapToObj(i -> {
                try {
                    Request activitiesGetRequest = RouteTestUtil.buildAuthorizedPostRequest(token, url,
                            gson.toJson(new ActivityInstanceCreationPayload(parentActivity.getActivityCode())));
                    HttpResponse response = activitiesGetRequest.execute().returnResponse();
                    ActivityInstanceCreationResponse creationResponse = gson.fromJson(EntityUtils.toString(response.getEntity()),
                            ActivityInstanceCreationResponse.class);
                    if (creationResponse.getInstanceGuid() != null) {
                        activityInstancesToDelete.add(creationResponse.getInstanceGuid());
                    }
                    return response.getStatusLine().getStatusCode();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(toList());
            // we attempted 3 creations, but only one should have succeeded
            assertEquals(4, returnCodes.size());
            assertEquals(1, returnCodes.stream().filter(each -> each == 200).count());
            assertEquals(3, returnCodes.stream().filter(each -> each == 422).count());
        } finally {
            TransactionWrapper.useTxn(handle -> {
                assertEquals(1, handle.attach(JdbiActivity.class).updateMaxInstancesPerUserById(parentActivity.getActivityId(), 1));
                List<ActivityInstanceDto> allActivityInstances =
                        handle.attach(JdbiActivityInstance.class).findAllByUserGuidAndActivityCode(testData.getUserGuid(),
                                parentActivity.getActivityCode(), testData.getStudyId());
                assertEquals(1, allActivityInstances.size());
            });
        }
    }

    @Test
    public void testCreateChildActivity_missingParentInstanceGuid() {
        makeChildCreationRequest(nestedActivity.getActivityCode(), "")
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("requires parent instance guid"));
    }

    @Test
    public void testCreateChildActivity_parentInstanceNotFound() {
        makeChildCreationRequest(nestedActivity.getActivityCode(), "foobar")
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                .body("message", containsString("Could not find activity instance"));
    }

    @Test
    public void testCreateChildActivity_incorrectParentActivity() {
        String someInstanceGuid = makeCreationRequest(anotherActivity.getDef().getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");
        activityInstancesToDelete.add(someInstanceGuid);

        makeChildCreationRequest(nestedActivity.getActivityCode(), someInstanceGuid)
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("does not match"));
    }

    @Test
    public void testCreateChildActivity_parentInstanceReadOnly() {
        String parentInstanceGuid = makeCreationRequest(parentActivity.getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");
        parentInstancesToDelete.add(parentInstanceGuid);

        TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                .attach(JdbiActivityInstance.class)
                .updateIsReadonlyByGuid(true, parentInstanceGuid)));

        makeChildCreationRequest(nestedActivity.getActivityCode(), parentInstanceGuid)
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY))
                .body("message", containsString("read-only"));
    }

    @Test
    public void testCreateChildActivityAndParentInstanceBlockVisibility_success() {
        String parentInstanceGuid = makeCreationRequest(parentActivity.getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");
        parentInstancesToDelete.add(parentInstanceGuid);

        ValidatableResponse response = makeChildCreationRequest(nestedActivity.getActivityCode(), parentInstanceGuid)
                .statusCode(200).contentType(ContentType.JSON);

        String instanceGuid = response.extract().path("instanceGuid");
        assertTrue(StringUtils.isNotBlank(instanceGuid));
        activityInstancesToDelete.add(instanceGuid);

        ActivityInstanceDto instance = TransactionWrapper.withTxn(handle -> handle
                .attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(instanceGuid)
                .orElse(null));
        assertNotNull(instance);
        assertEquals(parentInstanceGuid, instance.getParentInstanceGuid());

        List<BlockVisibility> blockVisibility = response.extract().path("blockVisibility");
        assertNotNull(blockVisibility);
        assertEquals(1, blockVisibility.size());
    }

    @Test
    public void testStudyAdmin_createChildActivity_parentInstanceReadOnly() {
        String parentInstanceGuid = makeCreationRequest(parentActivity.getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");
        parentInstancesToDelete.add(parentInstanceGuid);

        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.attach(JdbiActivityInstance.class)
                    .updateIsReadonlyByGuid(true, parentInstanceGuid));
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
        });

        try {
            String instanceGuid = makeChildCreationRequest(nestedActivity.getActivityCode(), parentInstanceGuid)
                    .statusCode(200).contentType(ContentType.JSON)
                    .and().extract().path("instanceGuid");
            assertTrue(StringUtils.isNotBlank(instanceGuid));
            activityInstancesToDelete.add(instanceGuid);
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(AuthDao.class)
                    .removeAdminFromAllStudies(testData.getUserId()));
        }
    }
}
