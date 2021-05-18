package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static java.util.Collections.emptyList;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.broadinstitute.ddp.constants.RouteConstants.API.ADMIN_STUDY_PARTICIPANTS_LOOKUP;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;
import static org.broadinstitute.ddp.route.AdminParticipantsLookupRoute.DEFAULT_PARTICIPANTS_LOOKUP_RESULT_MAX_COUNT;
import static org.broadinstitute.ddp.route.AdminParticipantsLookupRouteTest.ParticipantsLookupTestService.QUERY_ELASTIC_SEARCH_STATUS__UNAUTHORIZED;
import static org.elasticsearch.rest.RestStatus.UNAUTHORIZED;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;

import com.google.common.base.Strings;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.SparkServerAwareBaseTest;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.participantslookup.ESParticipantsLookupService;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupPayload;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.service.participantslookup.ParticipantLookupType;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RestHighLevelClient;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.Spark;

/**
 * Unit tests for testing {@link AdminParticipantsLookupRoute}
 */
public class AdminParticipantsLookupRouteTest extends SparkServerAwareBaseTest {

    private static SparkServerTestRunner sparkServerTestRunner = new SparkServerTestRunner();

    @BeforeClass
    public static void setup() {
        sparkServerTestRunner.setupSparkServer(
                AdminParticipantsLookupRouteTest::mapFiltersBeforeRoutes,
                AdminParticipantsLookupRouteTest::mapRoutes,
                AdminParticipantsLookupRouteTest::buildUrlTemplate
        );
    }

    @AfterClass
    public static void tearDown() {
        sparkServerTestRunner.tearDownSparkServer();
    }

    protected static boolean mapRoutes() {
        Spark.post(ADMIN_STUDY_PARTICIPANTS_LOOKUP,
                new AdminParticipantsLookupRoute(new AdminParticipantsLookupRouteTest.ParticipantsLookupTestService(null)), jsonSerializer);
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
                .statusCode(SC_OK).contentType(ContentType.JSON)
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
                .statusCode(SC_OK).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(1))
                .body("results[0].firstName", equalTo("Pete"))
                .body("results[0].lastName", equalTo("Koolman"))
                .body("results[0].guid", equalTo("guid_1"))
                .body("totalCount", equalTo(1));
    }

    @Test
    public void testQueryWithTooLongParameter() {
        // generate query longer than max count
        String tooLongQuery = Strings.padEnd("query", DEFAULT_PARTICIPANTS_LOOKUP_RESULT_MAX_COUNT, '#');
        var payload = new ParticipantsLookupPayload(tooLongQuery);
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(SC_BAD_REQUEST);
    }

    @Test
    public void testQueryWithElasticSearchUnauthorized() {
        var payload = new ParticipantsLookupPayload(QUERY_ELASTIC_SEARCH_STATUS__UNAUTHORIZED);
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(SC_INTERNAL_SERVER_ERROR).contentType(ContentType.JSON)
                .body(RESPONSE_BODY_PARAM_CODE, equalTo(UNAUTHORIZED.name()));
    }


    public static class ParticipantsLookupTestService extends ESParticipantsLookupService {

        static final String QUERY__EMPTY_RESULT = "empty_result";
        static final String QUERY__SINGLE_RESULT = "single_result";
        static final String QUERY_ELASTIC_SEARCH_STATUS__UNAUTHORIZED = "ES_unauthorized";

        public ParticipantsLookupTestService(RestHighLevelClient esClient) {
            super(esClient);
        }

        @Override
        protected void doLookupParticipants(
                ParticipantLookupType participantLookupType,
                StudyDto studyDto,
                String query,
                Integer resultsMaxCount,
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
            } else if (query.equals(QUERY_ELASTIC_SEARCH_STATUS__UNAUTHORIZED)) {
                throw new ElasticsearchStatusException("ES unauthorized", UNAUTHORIZED);
            }
        }
    }
}
