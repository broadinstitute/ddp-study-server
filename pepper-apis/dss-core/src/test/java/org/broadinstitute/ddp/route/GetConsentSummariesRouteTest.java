package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Arrays;

import com.google.gson.Gson;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetConsentSummariesRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String noElectionsActCode;
    private static String twoElectionsActCode;
    private static String userGuid;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            userGuid = testData.getUserGuid();
            setupConsentActivities(handle);
        });
        String endpoint = API.USER_STUDIES_ALL_CONSENTS
                .replace(PathParam.USER_GUID, userGuid)
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid());
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static void setupConsentActivities(Handle handle) {
        ActivityDao actDao = handle.attach(ActivityDao.class);
        long timestamp = Instant.now().toEpochMilli();

        noElectionsActCode = "CON_LIST_ROUTE_NO_ELECTIONS_" + timestamp;
        String consentExpr = "true";
        ConsentActivityDef consent = ConsentActivityDef.builder(noElectionsActCode, "v1", testData.getStudyGuid(), consentExpr)
                .addName(new Translation("en", "activity " + noElectionsActCode))
                .build();
        actDao.insertConsent(consent, RevisionMetadata.now(testData.getUserId(), "add " + noElectionsActCode));
        assertNotNull(consent.getActivityId());

        twoElectionsActCode = "CON_LIST_ROUTE_TWO_ELECTIONS_" + timestamp;
        consentExpr = "false";
        consent = ConsentActivityDef.builder(twoElectionsActCode, "v1", testData.getStudyGuid(), consentExpr)
                .addName(new Translation("en", "activity " + twoElectionsActCode))
                .addElection(new ConsentElectionDef("ELECTION1", "false"))
                .addElection(new ConsentElectionDef("ELECTION2", "true && false"))
                .build();
        actDao.insertConsent(consent, RevisionMetadata.now(testData.getUserId(), "add " + twoElectionsActCode));
        assertNotNull(consent.getActivityId());
    }

    @Test
    public void testGet_hasConsentActivitiesForTestStudy() {
        given().auth().oauth2(token)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$", hasSize(2))
                .body("activityCode", contains(noElectionsActCode, twoElectionsActCode));
    }

    @Test
    public void testGet_evaluatesConsentStatusWhenInstanceIsFound() {
        String instanceGuid = TransactionWrapper.withTxn(handle -> {
            long activityId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(testData.getStudyId(), twoElectionsActCode).get();
            return handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activityId, userGuid)
                    .getGuid();
        });
        try {
            Response resp = given().auth().oauth2(token)
                    .when().get(url).then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("$", hasSize(2))
                    .extract().response();

            ConsentSummary[] summaries = new Gson().fromJson(resp.body().asString(), ConsentSummary[].class);

            ConsentSummary summary = Arrays.stream(summaries)
                    .filter(s -> noElectionsActCode.equals(s.getActivityCode()))
                    .findFirst().get();
            assertNull(summary.getInstanceGuid());
            assertNull(summary.getConsented());
            assertTrue(summary.getElections().isEmpty());

            summary = Arrays.stream(summaries)
                    .filter(s -> twoElectionsActCode.equals(s.getActivityCode()))
                    .findFirst().get();
            assertEquals(instanceGuid, summary.getInstanceGuid());
            assertFalse(summary.getConsented());
            assertEquals(2, summary.getElections().size());
            for (ConsentElection election : summary.getElections()) {
                assertFalse(election.getSelected());
            }
        } finally {
            TransactionWrapper.useTxn(handle ->
                    handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(instanceGuid));
        }
    }
}
