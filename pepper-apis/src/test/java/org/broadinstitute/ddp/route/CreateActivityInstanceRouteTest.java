package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertNotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.json.ActivityInstanceCreationPayload;
import org.broadinstitute.ddp.json.ActivityInstanceCreationResponse;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.hamcrest.Matchers;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateActivityInstanceRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateActivityInstanceRouteTest.class);
    private static final Collection<String> activityInstancesToDelete = new HashSet<>();
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String ACTIVITY_CODE;
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

            FormActivityDef activityDef = setupActivityDefinition(handle);
            activityDef.setMaxInstancesPerUser(1);
            insertTestActivity(handle, activityDef);

            ACTIVITY_CODE = activityDef.getActivityCode();
            token = testData.getTestingUser().getToken();
        });
    }


    private static void insertTestActivity(Handle handle, FormActivityDef act) {
        long millis = Instant.now().toEpochMilli();
        String changeReason = "test for " + CreateActivityInstanceRouteTest.class.getName();
        long revId = handle.attach(JdbiRevision.class).insert(testData.getUserId(),
                millis,
                null,
                changeReason);
        handle.attach(FormActivityDao.class).insertActivity(act, revId);
        assertNotNull(act.getActivityId());
    }

    private static FormActivityDef setupActivityDefinition(Handle handle) {
        String activityCode = DBUtils.uniqueStandardGuid(handle, SqlConstants.StudyActivityTable.TABLE_NAME,
                SqlConstants.StudyActivityTable.CODE);
        FormActivityDef activityDef = FormActivityDef.formBuilder(FormType.PREQUALIFIER,
                activityCode,
                "v1",
                testData.getStudyGuid())
                .addName(new Translation("en", "CreateActivityInstanceRouteTest testing"))
                .build();
        return activityDef;

    }

    @After
    public void deleteCreatedInstances() throws SQLException {
        TransactionWrapper.useTxn(handle -> {
            try (PreparedStatement deleteActivityInstance = handle.getConnection().prepareStatement(
                    "delete from activity_instance where activity_instance_guid = ?");
                    PreparedStatement deleteStatus = handle.getConnection().prepareStatement(
                            "delete from activity_instance_status where activity_instance_id ="
                                    + " (select activity_instance_id from activity_instance "
                                    + "where activity_instance_guid = ?)")) {
                for (String instanceGuid : activityInstancesToDelete) {
                    deleteStatus.setString(1, instanceGuid);
                    deleteStatus.executeUpdate();
                    deleteActivityInstance.setString(1, instanceGuid);
                    int numDeleted = deleteActivityInstance.executeUpdate();
                    if (numDeleted != 1) {
                        throw new DaoException("Deleted " + numDeleted
                                + " rows instead of a single row for activity instance " + instanceGuid);
                    }
                    LOG.info("Deleted {} rows for test activity instance {}", numDeleted, instanceGuid);
                }
            }
            // Reset activity precondition
            JdbiExpression exprDao = handle.attach(JdbiExpression.class);
        });
        activityInstancesToDelete.clear();
    }

    private Response createActivityInstance(String activityCode) {
        ActivityInstanceCreationPayload payload = new ActivityInstanceCreationPayload(activityCode);
        return given().body(payload, ObjectMapperType.GSON).auth().oauth2(token).when().post(url).andReturn();
    }

    @Test
    public void testInvalidJson() throws Exception {
        Assert.assertEquals(400, given().body("wicked bad payload").auth().oauth2(token).when().post(url).andReturn()
                .getStatusCode());
    }

    @Test
    public void testMissingActivityCode() throws Exception {
        ActivityInstanceCreationPayload payload = new ActivityInstanceCreationPayload(null);
        Assert.assertEquals(422, given().body(payload).auth().oauth2(token).when().post(url).andReturn()
                .getStatusCode());
    }

    @Test
    public void testCreateActivityNoExistingInstancesShouldSucceed() throws SQLException {
        Response response = createActivityInstance(ACTIVITY_CODE);
        Assert.assertEquals(200, response.getStatusCode());

        ActivityInstanceCreationResponse creationResponse = new Gson().fromJson(response.getBody().asString(),
                ActivityInstanceCreationResponse.class);
        Assert.assertTrue(StringUtils.isNotBlank(creationResponse.getInstanceGuid()));
        activityInstancesToDelete.add(creationResponse.getInstanceGuid());

        long numRowsWritten = TransactionWrapper.withTxn(handle -> {
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(
                    "select count(1) from activity_instance where activity_instance_guid = ?")) {
                stmt.setString(1, creationResponse.getInstanceGuid());

                ResultSet rs = stmt.executeQuery();
                rs.next();
                return rs.getLong(1);
            }
        });
        Assert.assertEquals(1, numRowsWritten);
    }

    @Test
    public void testCreateActivityShouldFailBecauseTooManyInstances() {
        Response response = createActivityInstance(ACTIVITY_CODE);
        Assert.assertEquals(200, response.getStatusCode());

        String instanceGuid = response.path("instanceGuid");
        Assert.assertNotNull(instanceGuid);
        activityInstancesToDelete.add(instanceGuid);

        createActivityInstance(ACTIVITY_CODE)
                .then().assertThat()
                .statusCode(422)
                .contentType(ContentType.JSON)
                .body("code", Matchers.equalTo(ErrorCodes.TOO_MANY_INSTANCES));
    }
}
