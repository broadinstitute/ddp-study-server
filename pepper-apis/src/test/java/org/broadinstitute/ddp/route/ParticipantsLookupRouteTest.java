package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static java.util.Collections.emptyList;
import static org.broadinstitute.ddp.constants.RouteConstants.API.ADMIN_STUDY_PARTICIPANTS_LOOKUP;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.SparkServerAwareBaseTest;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupPayload;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.Spark;

public class ParticipantsLookupRouteTest extends SparkServerAwareBaseTest {

    private static SparkServerTestRunner sparkServerTestRunner = new SparkServerTestRunner();

    @BeforeClass
    public static void setup() {
        sparkServerTestRunner.setupSparkServer(
                ParticipantsLookupRouteTest::mapFiltersBeforeRoutes,
                ParticipantsLookupRouteTest::mapRoutes,
                ParticipantsLookupRouteTest::buildUrlTemplate
        );
    }

    @AfterClass
    public static void tearDown() {
        sparkServerTestRunner.tearDownSparkServer();
    }

    protected static boolean mapRoutes() {
        Spark.post(ADMIN_STUDY_PARTICIPANTS_LOOKUP,
                new ParticipantsLookupRoute(new ParticipantsLookupRouteTest.ParticipantsLookupTestService()), jsonSerializer);
        return true;
    }

    protected static String buildUrlTemplate() {
        return LOCALHOST + port + ADMIN_STUDY_PARTICIPANTS_LOOKUP.replace(STUDY_GUID, PLACEHOLDER__STUDY);
    }


    @Test
    public void testQueryWithEmptyResult() {
        var payload = new ParticipantsLookupPayload(ParticipantsLookupTestService.QUERY__EMPTY_RESULT);
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(0))
                .body("totalCount", equalTo(0));
    }

    @Test
    public void testQueryWithOneResult() {
        var payload = new ParticipantsLookupPayload(ParticipantsLookupTestService.QUERY__SINGLE_RESULT);
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(1))
                .body("results[0].firstName", equalTo("Pete"))
                .body("results[0].lastName", equalTo("Koolman"))
                .body("results[0].guid", equalTo("guid_1"))
                .body("totalCount", equalTo(1));
    }


    public static class ParticipantsLookupTestService extends ParticipantsLookupService {

        static final String QUERY__EMPTY_RESULT = "empty_result";
        static final String QUERY__SINGLE_RESULT = "single_result";

        @Override
        protected void doLookupParticipants(String studyGuid, String query, int resultsMaxCount,
                                            ParticipantsLookupResult participantsLookupResult) throws Exception {
            if (query.equals(QUERY__EMPTY_RESULT)) {
                participantsLookupResult.setTotalCount(0);
                participantsLookupResult.setResultRows(emptyList());
            } else if (query.equals(QUERY__SINGLE_RESULT)) {
                participantsLookupResult.setTotalCount(1);
                var results = new ArrayList<>();
                var row = new ParticipantsLookupResultRow();
                row.setEmail("test@datadonationplatform.org");
                row.setFirstName("Pete");
                row.setLastName("Koolman");
                row.setGuid("guid_1");
                row.setHruid("hruid_1");
                results.add(row);
                participantsLookupResult.setResultRows(results);
            }
        }
    }
}
