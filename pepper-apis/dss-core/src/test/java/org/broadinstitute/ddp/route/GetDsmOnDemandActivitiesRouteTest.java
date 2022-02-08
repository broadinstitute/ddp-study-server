package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.constants.RouteConstants.API;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import static org.broadinstitute.ddp.model.dsm.OnDemandActivity.RepeatType.NONREPEATING;
import static org.broadinstitute.ddp.model.dsm.OnDemandActivity.RepeatType.REPEATING;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;

import io.restassured.http.ContentType;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetDsmOnDemandActivitiesRouteTest extends DsmRouteTest {

    private static String url;

    @BeforeClass
    public static void setupRouteTest() {
        String endpoint = API.DSM_ONDEMAND_ACTIVITIES
                .replace(PathParam.STUDY_GUID, "{studyGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @Test
    public void test_nonExistentStudy_returns404() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", "non-existent-study")
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.STUDY_NOT_FOUND))
                .body("message", containsString("study"));
    }

    @Test
    public void test_noOndemandActivities_returnsEmptyList() {

        StudyDto studyDto = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateTestStudy(handle, ConfigManager.getInstance().getConfig());
        });

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyDto.getGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(0));
    }

    @Test
    public void test_singleOndemandActivity_nonrepeating() {

        StudyDto studyDto = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateTestStudy(handle, ConfigManager.getInstance().getConfig());
        });
        FormActivityDef activity = TransactionWrapper.withTxn(handle -> insertActivity(handle, studyDto.getGuid(), true, 1));

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyDto.getGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .root("[0]")
                .body("name", equalTo(activity.getActivityCode()))
                .body("type", equalTo(NONREPEATING.name()));
    }

    @Test
    public void test_multipleOndemandActivities_repeating() {
        StudyDto studyDto = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateTestStudy(handle, ConfigManager.getInstance().getConfig());
        });
        FormActivityDef act1 = TransactionWrapper.withTxn(handle -> insertActivity(handle, studyDto.getGuid(), true, null));
        FormActivityDef act2 = TransactionWrapper.withTxn(handle -> insertActivity(handle, studyDto.getGuid(), true, 2));
        FormActivityDef act3 = TransactionWrapper.withTxn(handle -> insertActivity(handle, studyDto.getGuid(), true, 100));

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyDto.getGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(3))
                .body("name", contains(act1.getActivityCode(), act2.getActivityCode(), act3.getActivityCode()))
                .body("type", everyItem(equalTo(REPEATING.name())));
    }

    @Test
    public void test_nonOndemandActivities_notReturned() {
        StudyDto studyDto = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateTestStudy(handle, ConfigManager.getInstance().getConfig());
        });
        FormActivityDef expected = TransactionWrapper.withTxn(handle -> insertActivity(handle, studyDto.getGuid(), true, 0));
        FormActivityDef notExpected = TransactionWrapper.withTxn(handle -> insertActivity(handle, studyDto.getGuid(), false, 1));

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyDto.getGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(1))
                .root("[0]")
                .body("name", equalTo(expected.getActivityCode()))
                .body("type", equalTo(NONREPEATING.name()));
    }

    private FormActivityDef insertActivity(Handle handle, String studyGuid, boolean allowOndemandTrigger, Integer maxInstances) {
        String code = "DSM_ONDEMAND_ACTIVITIES_TEST" + Instant.now().toEpochMilli();
        FormActivityDef activity = FormActivityDef.generalFormBuilder(code, "v1", studyGuid)
                .addName(new Translation("en", "test activity for GetDsmOnDemandActivitiesRoute"))
                .setAllowOndemandTrigger(allowOndemandTrigger)
                .setMaxInstancesPerUser(maxInstances)
                .build();
        handle.attach(ActivityDao.class).insertActivity(activity, RevisionMetadata.now(generatedTestData.getUserId(), "test"));
        assertNotNull(activity.getActivityId());
        return activity;
    }
}
