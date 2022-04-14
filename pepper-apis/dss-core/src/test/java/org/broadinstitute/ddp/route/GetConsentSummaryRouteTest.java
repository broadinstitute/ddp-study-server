package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetConsentSummaryRouteTest extends IntegrationTestSuite.TestCase {

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
        String endpoint = API.USER_STUDIES_CONSENT
                .replace(PathParam.USER_GUID, userGuid)
                .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(PathParam.ACTIVITY_CODE, "{activityCode}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    private static void setupConsentActivities(Handle handle) {
        ActivityDao actDao = handle.attach(ActivityDao.class);
        long timestamp = Instant.now().toEpochMilli();

        noElectionsActCode = "CON_ROUTE_NO_ELECTIONS_" + timestamp;
        String consentExpr = "true";
        ConsentActivityDef consent = ConsentActivityDef.builder(noElectionsActCode, "v1", testData.getStudyGuid(), consentExpr)
                .addName(new Translation("en", "activity " + noElectionsActCode))
                .build();
        actDao.insertConsent(consent, RevisionMetadata.now(testData.getUserId(), "add " + noElectionsActCode));
        assertNotNull(consent.getActivityId());

        twoElectionsActCode = "CON_ROUTE_TWO_ELECTIONS_" + timestamp;
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
    public void testGet_noConsentActivityFound() {
        given().auth().oauth2(token)
                .pathParam("activityCode", "abc")
                .when().get(url).then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.ACTIVITY_NOT_FOUND));
    }

    @Test
    public void testGet_consentActivityFound() {
        given().auth().oauth2(token)
                .pathParam("activityCode", noElectionsActCode)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("activityCode", equalTo(noElectionsActCode))
                .body("instanceGuid", is(nullValue()))
                .body("consented", is(nullValue()))
                .body("elections", hasSize(0));
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
            given().auth().oauth2(token)
                    .pathParam("activityCode", twoElectionsActCode)
                    .when().get(url).then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("activityCode", equalTo(twoElectionsActCode))
                    .body("instanceGuid", equalTo(instanceGuid))
                    .body("consented", equalTo(false))
                    .body("elections", hasSize(2))
                    .body("elections[0].selected", equalTo(false))
                    .body("elections[1].selected", equalTo(false));
        } finally {
            TransactionWrapper.useTxn(handle ->
                    handle.attach(ActivityInstanceDao.class).deleteByInstanceGuid(instanceGuid));
        }
    }
}
