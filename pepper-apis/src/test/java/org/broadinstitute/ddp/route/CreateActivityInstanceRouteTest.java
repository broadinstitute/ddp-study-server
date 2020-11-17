package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashSet;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.ActivityInstanceCreationPayload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateActivityInstanceRouteTest extends IntegrationTestSuite.TestCase {

    private static final Collection<String> activityInstancesToDelete = new HashSet<>();
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static TestFormActivity act;
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

            act = TestFormActivity.builder()
                    .setMaxInstancesPerUser(1)
                    .setHideExistingInstances(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            token = testData.getTestingUser().getToken();
        });
    }

    @After
    public void deleteCreatedInstances() {
        TransactionWrapper.useTxn(handle -> {
            var instanceDao = handle.attach(ActivityInstanceDao.class);
            for (var instanceGuid : activityInstancesToDelete) {
                instanceDao.deleteByInstanceGuid(instanceGuid);
            }
        });
        activityInstancesToDelete.clear();
    }

    private ValidatableResponse makeCreationRequest(String activityCode) {
        ActivityInstanceCreationPayload payload = new ActivityInstanceCreationPayload(activityCode);
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
        String instanceGuid = makeCreationRequest(act.getDef().getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");

        assertTrue(StringUtils.isNotBlank(instanceGuid));
        activityInstancesToDelete.add(instanceGuid);

        ActivityInstanceDto instance = TransactionWrapper.withTxn(handle -> handle
                .attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(instanceGuid)
                .orElse(null));
        assertNotNull(instance);
    }

    @Test
    public void testCreateActivityShouldFailBecauseTooManyInstances() {
        String instanceGuid = makeCreationRequest(act.getDef().getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");

        assertTrue(StringUtils.isNoneBlank(instanceGuid));
        activityInstancesToDelete.add(instanceGuid);

        makeCreationRequest(act.getDef().getActivityCode())
                .statusCode(422)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.TOO_MANY_INSTANCES));
    }

    @Test
    public void testCreateActivityShouldHideExistingInstances() {
        TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                .attach(JdbiActivity.class)
                .updateMaxInstancesPerUserById(act.getDef().getActivityId(), null)));

        String firstInstanceGuid = makeCreationRequest(act.getDef().getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");

        assertTrue(StringUtils.isNoneBlank(firstInstanceGuid));
        activityInstancesToDelete.add(firstInstanceGuid);

        String secondInstanceGuid = makeCreationRequest(act.getDef().getActivityCode())
                .statusCode(200).and().extract().path("instanceGuid");

        assertTrue(StringUtils.isNoneBlank(secondInstanceGuid));
        activityInstancesToDelete.add(secondInstanceGuid);

        try {
            ActivityInstanceDto firstInstance = TransactionWrapper.withTxn(handle -> handle
                    .attach(JdbiActivityInstance.class)
                    .getByActivityInstanceGuid(firstInstanceGuid)
                    .orElse(null));
            assertNotNull(firstInstance);
            assertTrue(firstInstance.isHidden());
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(1, handle
                    .attach(JdbiActivity.class)
                    .updateMaxInstancesPerUserById(act.getDef().getActivityId(), 1)));
        }
    }
}
