package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.dsm.TriggeredInstance;
import org.broadinstitute.ddp.model.dsm.TriggeredInstanceStatusType;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetDsmTriggeredInstancesRouteTest extends DsmRouteTest {

    private static String url;
    private static FormActivityDef activity;

    private Set<String> instanceGuidsToDelete = new HashSet<>();

    @BeforeClass
    public static void setupRouteTest() {
        String endpoint = RouteConstants.API.DSM_ONDEMAND_ACTIVITY
                .replace(PathParam.STUDY_GUID, generatedTestData.getStudyGuid())
                .replace(PathParam.ACTIVITY_CODE, "{activityCode}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
        activity = TransactionWrapper.withTxn(handle -> insertDummyActivity(handle));
    }

    private static FormActivityDef insertDummyActivity(Handle handle) {
        String code = "DSM_ONDEMAND_TRIGGER_TEST" + Instant.now().toEpochMilli();
        FormActivityDef activity = FormActivityDef.generalFormBuilder(code, "v1", generatedTestData.getStudyGuid())
                .addName(new Translation("en", "test activity for GetDsmTriggeredInstancesRoute"))
                .setAllowOndemandTrigger(true)
                .build();
        handle.attach(ActivityDao.class).insertActivity(activity, RevisionMetadata.now(generatedTestData.getUserId(), "test"));
        assertNotNull(activity.getActivityId());
        return activity;
    }

    @After
    public void deleteInstances() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = null, legacy_shortid = null"
                    + " where guid = :guid")
                    .bind("guid", userGuid)
                    .execute());
        });
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            for (String guid : instanceGuidsToDelete) {
                instanceDao.deleteByInstanceGuid(guid);
            }
        });
        instanceGuidsToDelete.clear();
    }

    @Test
    public void test_nonExistentStudy_returns404() {
        String urlPath = url.replace(generatedTestData.getStudyGuid(), "non-existent-study");
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", "abc")
                .when().get(urlPath)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.STUDY_NOT_FOUND))
                .body("message", containsString("study"));
    }

    @Test
    public void test_nonExistentActivity_returns404() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", "non-existent-activity")
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND))
                .body("message", containsString("activity"));
    }

    @Test
    public void test_activityNotTriggered_returnsEmptyList() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(0));
    }

    @Test
    public void test_instanceNotFromOndemandTrigger_notReturned() {
        TransactionWrapper.useTxn(handle ->
                insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), null));

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(0));
    }

    @Test
    public void test_activityTriggered_createdInstanceReturnsStartedStatus() {
        InstanceStatusType initialStatus = InstanceStatusType.CREATED;
        TriggeredInstanceStatusType expectedStatus = TriggeredInstanceStatusType.STARTED;

        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), new Long(0), initialStatus));

        String resp = given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .root("[0]")
                .body("surveyStatus", equalTo(expectedStatus.name()))
                .extract().response().asString();

        List<TriggeredInstance> res = new Gson().fromJson(resp, new TypeToken<ArrayList<TriggeredInstance>>() {
        }.getType());
        TriggeredInstance actual = res.get(0);
        assertThat(actual.getStatus(), equalTo(expectedStatus));
    }

    @Test
    public void test_activityTriggered_inProgressInstanceReturnsStartedStatus() {
        InstanceStatusType initialStatus = InstanceStatusType.IN_PROGRESS;
        TriggeredInstanceStatusType expectedStatus = TriggeredInstanceStatusType.STARTED;

        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), new Long(0), initialStatus));

        String resp = given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .root("[0]")
                .body("surveyStatus", equalTo(expectedStatus.name()))
                .extract().response().asString();

        List<TriggeredInstance> res = new Gson().fromJson(resp, new TypeToken<ArrayList<TriggeredInstance>>() {
        }.getType());
        TriggeredInstance actual = res.get(0);
        assertThat(actual.getStatus(), equalTo(expectedStatus));
    }

    @Test
    public void test_activityTriggered_completeInstanceReturnsCompleteStatus() {
        InstanceStatusType initialStatus = InstanceStatusType.COMPLETE;
        TriggeredInstanceStatusType expectedStatus = TriggeredInstanceStatusType.COMPLETE;

        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), new Long(0), initialStatus));

        String resp = given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .root("[0]")
                .body("surveyStatus", equalTo(expectedStatus.name()))
                .extract().response().asString();

        List<TriggeredInstance> res = new Gson().fromJson(resp, new TypeToken<ArrayList<TriggeredInstance>>() {
        }.getType());
        TriggeredInstance actual = res.get(0);
        assertThat(actual.getStatus(), equalTo(expectedStatus));
    }

    @Test
    public void test_activityTriggered_returnsTriggeredInstanceData() {
        long triggerId = Instant.now().toEpochMilli();
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), triggerId));

        String resp = given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .root("[0]")
                .body("participantId", equalTo(generatedTestData.getUserGuid()))
                .body("shortId", equalTo(generatedTestData.getUserHruid()))
                .body("survey", equalTo(activity.getActivityCode()))
                .body("followUpInstance", equalTo(instanceDto.getGuid()))
                .body("surveyStatus", equalTo(TriggeredInstanceStatusType.STARTED.name()))
                .body("triggerId", equalTo(triggerId))
                .extract().response().asString();

        List<TriggeredInstance> res = new Gson().fromJson(resp, new TypeToken<ArrayList<TriggeredInstance>>() {
        }.getType());
        TriggeredInstance actual = res.get(0);
        assertThat(actual.getCreatedAtSec(), equalTo(instanceDto.getCreatedAtMillis() / 1000));
    }

    @Test
    public void test_participantWithLegacyAltpid_activityTriggered_returnsTriggeredInstanceData() {
        String legacyAltPid = "12345.GUID-GUID-GUID";
        String legacyShortId = "12345.LEGACY-SHORTID";
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid, "
                    + " legacy_shortid = :legacyShortId where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("legacyShortId", legacyShortId)
                    .bind("guid", userGuid)
                    .execute());
        });

        long triggerId = Instant.now().toEpochMilli();
        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle ->
                insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), triggerId));

        String resp = given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .root("[0]")
                .body("participantId", equalTo(legacyAltPid))
                .body("shortId", equalTo(generatedTestData.getUserHruid()))
                .body("survey", equalTo(activity.getActivityCode()))
                .body("legacyShortId", equalTo(legacyShortId))
                .body("followUpInstance", equalTo(instanceDto.getGuid()))
                .body("surveyStatus", equalTo(TriggeredInstanceStatusType.STARTED.name()))
                .body("triggerId", equalTo(triggerId))
                .extract().response().asString();

        List<TriggeredInstance> res = new Gson().fromJson(resp, new TypeToken<ArrayList<TriggeredInstance>>() {
        }.getType());
        TriggeredInstance actual = res.get(0);
        assertThat(actual.getCreatedAtSec(), equalTo(instanceDto.getCreatedAtMillis() / 1000));

        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = null where guid = :guid")
                    .bind("guid", userGuid)
                    .execute());
        });
    }

    @Test
    public void test_multipleActivityTriggered_returnsTriggeredInstanceData() {
        long triggerId = Instant.now().toEpochMilli();
        TransactionWrapper.useTxn(handle -> {
            insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), triggerId);
            insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), triggerId);
            insertInstanceAndDeferCleanup(handle, Instant.now().toEpochMilli(), triggerId);
        });

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("activityCode", activity.getActivityCode())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(3))
                .body("participantId", everyItem(equalTo(generatedTestData.getUserGuid())))
                .body("survey", everyItem(equalTo(activity.getActivityCode())))
                .body("triggerId", everyItem(equalTo(triggerId)));
    }

    private ActivityInstanceDto insertInstanceAndDeferCleanup(Handle handle, long createdAtMillis, Long triggerId) {
        return insertInstanceAndDeferCleanup(handle, createdAtMillis, triggerId, InstanceStatusType.CREATED);
    }

    private ActivityInstanceDto insertInstanceAndDeferCleanup(Handle handle,
                                                              long createdAtMillis,
                                                              Long triggerId,
                                                              InstanceStatusType status) {
        ActivityInstanceDto instanceDto = handle.attach(ActivityInstanceDao.class)
                .insertInstance(activity.getActivityId(),
                        generatedTestData.getUserGuid(), generatedTestData.getUserGuid(),
                        status, false, createdAtMillis, triggerId);
        assertNotNull(instanceDto);
        assertNotNull(instanceDto.getGuid());
        instanceGuidsToDelete.add(instanceDto.getGuid());
        return instanceDto;
    }
}
