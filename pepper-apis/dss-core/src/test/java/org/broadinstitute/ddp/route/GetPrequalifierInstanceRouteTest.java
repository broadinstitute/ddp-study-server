package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetPrequalifierInstanceRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static ActivityInstanceDto instanceDto;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            setupPrequalActivityAndInstance(handle);
        });
        String endpoint = API.USER_STUDIES_PREQUALIFIER
                .replace(PathParam.USER_GUID, testData.getUserGuid())
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid());
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static void setupPrequalActivityAndInstance(Handle handle) {
        String code = "PREQUAL_ROUTE_ACT" + Instant.now().toEpochMilli();
        FormActivityDef prequal = FormActivityDef.formBuilder(FormType.PREQUALIFIER, code, "v1", testData.getStudyGuid())
                .addName(new Translation("en", "activity name"))
                .addTitle(new Translation("en", "activity " + code))
                .addSubtitle(new Translation("en", "subtitle " + code))
                .build();
        handle.attach(ActivityDao.class).insertActivity(prequal, RevisionMetadata.now(testData.getUserId(), "add " + code));
        assertNotNull(prequal.getActivityId());
        instanceDto = handle.attach(ActivityInstanceDao.class).insertInstance(prequal.getActivityId(), testData.getUserGuid());
    }

    @AfterClass
    public static void cleanup() {
        TransactionWrapper.useTxn(handle ->
                handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(instanceDto.getGuid()));
    }

    @Test
    public void testGet_fallsBackToEnglish() {
        given().auth().oauth2(token)
                .header(RouteConstants.Header.ACCEPT_LANGUAGE, "abcxyz")
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("isoLanguageCode", equalTo("en"));
    }

    @Test
    public void testGet_defaultsToEnglish() {
        given().auth().oauth2(token)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("isoLanguageCode", equalTo("en"));
    }

    @Test
    public void testGet_prequalifierSummary() {
        given().auth().oauth2(token)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("guid", equalTo(instanceDto.getGuid()), "subtitle", startsWith("subtitle"));
    }
}
