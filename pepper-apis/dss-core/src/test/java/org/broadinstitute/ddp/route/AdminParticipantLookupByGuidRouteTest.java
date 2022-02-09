package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.broadinstitute.ddp.constants.RouteConstants.API.ADMIN_STUDY_PARTICIPANT_LOOKUP_BY_GUID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;
import static org.broadinstitute.ddp.route.AdminParticipantLookupByGuidRouteTest.ParticipantLookupByGuidTestService.USER_GUID__ELASTIC_SEARCH_STATUS__UNAUTHORIZED;
import static org.broadinstitute.ddp.route.AdminParticipantLookupByGuidRouteTest.ParticipantLookupByGuidTestService.USER_GUID__EMPTY_RESULT;
import static org.broadinstitute.ddp.route.AdminParticipantLookupByGuidRouteTest.ParticipantLookupByGuidTestService.USER_GUID__SINGLE_RESULT;
import static org.elasticsearch.rest.RestStatus.UNAUTHORIZED;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.SparkServerAwareBaseTest;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.participantslookup.ESParticipantsLookupService;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.service.participantslookup.ParticipantLookupType;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.Spark;

/**
 * Unit tests for testing {@link AdminParticipantLookupByGuidRoute}
 */
public class AdminParticipantLookupByGuidRouteTest extends SparkServerAwareBaseTest {

    private static SparkServerTestRunner sparkServerTestRunner = new SparkServerTestRunner();

    @BeforeClass
    public static void setup() {
        sparkServerTestRunner.setupSparkServer(
                AdminParticipantLookupByGuidRouteTest::mapFiltersBeforeRoutes,
                AdminParticipantLookupByGuidRouteTest::mapRoutes,
                AdminParticipantLookupByGuidRouteTest::buildUrlTemplate
        );
    }

    @AfterClass
    public static void tearDown() {
        sparkServerTestRunner.tearDownSparkServer();
    }

    protected static boolean mapRoutes() {
        Spark.get(ADMIN_STUDY_PARTICIPANT_LOOKUP_BY_GUID,
                new AdminParticipantLookupByGuidRoute(new ParticipantLookupByGuidTestService(null)), jsonSerializer);
        return true;
    }

    protected static String buildUrlTemplate() {
        return LOCALHOST + port + ADMIN_STUDY_PARTICIPANT_LOOKUP_BY_GUID
                .replace(STUDY_GUID, PLACEHOLDER__STUDY)
                .replace(USER_GUID, PLACEHOLDER__USER);
    }

    @Test
    public void testQueryWithResult() {
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", USER_GUID__SINGLE_RESULT)
                .when().get(urlTemplate)
                .then().assertThat()
                .statusCode(SC_OK).contentType(ContentType.JSON)
                .body("firstName", equalTo("Pete"))
                .body("lastName", equalTo("Koolman"))
                .body("hruid", equalTo("hruid_1"));
    }

    @Test
    public void testQueryWithElasticSearchUnauthorized() {
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", USER_GUID__ELASTIC_SEARCH_STATUS__UNAUTHORIZED)
                .when().get(urlTemplate)
                .then().assertThat()
                .statusCode(SC_INTERNAL_SERVER_ERROR).contentType(ContentType.JSON)
                .body(RESPONSE_BODY_PARAM_CODE, equalTo(UNAUTHORIZED.name()));
    }

    @Test
    public void testQueryWithWithEmptyResult() {
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .pathParam("user", USER_GUID__EMPTY_RESULT)
                .when().get(urlTemplate)
                .then().assertThat()
                .statusCode(SC_NOT_FOUND);
    }


    public static class ParticipantLookupByGuidTestService extends ESParticipantsLookupService {

        static final String USER_GUID__SINGLE_RESULT = "single_result";
        static final String USER_GUID__EMPTY_RESULT = "empty_result";
        static final String USER_GUID__ELASTIC_SEARCH_STATUS__UNAUTHORIZED = "ES_unauthorized";

        public ParticipantLookupByGuidTestService(RestHighLevelClient esClient) {
            super(esClient);
        }

        @Override
        protected void doLookupParticipants(
                ParticipantLookupType participantLookupType,
                StudyDto studyDto,
                String query,
                Integer resultsMaxCount,
                ParticipantsLookupResult participantsLookupResult) throws Exception {
            if (query.equals(USER_GUID__SINGLE_RESULT)) {
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
            } else if (query.equals(USER_GUID__EMPTY_RESULT)) {
                participantsLookupResult.setTotalCount(0);
                participantsLookupResult.setResultRows(new ArrayList<>());
            } else if (query.equals(USER_GUID__ELASTIC_SEARCH_STATUS__UNAUTHORIZED)) {
                throw new ElasticsearchStatusException("ES unauthorized", UNAUTHORIZED);
            }
        }
    }
}
