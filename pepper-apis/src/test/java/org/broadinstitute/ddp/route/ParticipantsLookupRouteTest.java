package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.filter.StudyAdminAuthFilter;
import org.broadinstitute.ddp.filter.TokenConverterFilter;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupPayload;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.security.JWTConverter;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.transformers.NullableJsonTransformer;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.Spark;

public class ParticipantsLookupRouteTest extends TxnAwareBaseTest {

    private static String urlTemplate;
    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setupServer() {
        var jsonSerializer = new NullableJsonTransformer();

        int port = RouteTestUtil.findOpenPortOrDefault(5559);
        Spark.port(port);
        Spark.before(RouteConstants.API.BASE + "/*", new TokenConverterFilter(new JWTConverter()));
        Spark.before(RouteConstants.API.ADMIN_BASE + "/*", new StudyAdminAuthFilter());
        Spark.post(RouteConstants.API.ADMIN_STUDY_PARTICIPANTS_LOOKUP,
                new ParticipantsLookupRoute(new ParticipantsLookupTestService(), 100), jsonSerializer);
        Spark.awaitInitialization();

        urlTemplate = "http://localhost:" + port + RouteConstants.API.ADMIN_STUDY_PARTICIPANTS_LOOKUP
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}");
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
            handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                    "http://localhost", testData.getAuth0ClientId(), testData.getTestingClient().getAuth0Domain());
        });
    }

    @AfterClass
    public static void tearDownServer() {
        Spark.stop();
        Spark.awaitStop();

        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiClient.class).updateWebPasswordRedirectUrlByAuth0ClientIdAndAuth0Domain(
                    null, testData.getAuth0ClientId(), testData.getTestingClient().getAuth0Domain());
            handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
        });
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
                .body("participants", Matchers.hasSize(0))
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
                .body("participants", Matchers.hasSize(1))
                .body("participants[0].firstName", equalTo("Pete"))
                .body("participants[0].lastName", equalTo("Koolman"))
                .body("participants[0].guid", equalTo("guid_1"))
                .body("totalCount", equalTo(1));
    }


    public static class ParticipantsLookupTestService implements ParticipantsLookupService {

        static final String QUERY__EMPTY_RESULT = "empty_result";
        static final String QUERY__SINGLE_RESULT = "single_result";

        @Override
        public ParticipantsLookupResult lookupParticipants(String studyGuid, String query, int resultsMaxCount) {
            ParticipantsLookupResult participantsLookupResult = new ParticipantsLookupResult();
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
            return participantsLookupResult;
        }
    }
}
