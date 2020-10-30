package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.json.GovernedUserRegistrationPayload;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class GovernedParticipantRegistrationRouteTest extends IntegrationTestSuite.TestCase {

    private static String token;
    private static String url;
    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {

        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
        });

        String endpoint = RouteConstants.API.USER_STUDY_PARTICIPANTS
                .replace(RouteConstants.PathParam.USER_GUID, "{userGuid}")
                .replace(RouteConstants.PathParam.STUDY_GUID, "{studyGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @Test
    public void invalidStudy() {
        GovernedUserRegistrationPayload payload = new GovernedUserRegistrationPayload("it", "John", "Doe",
                "Europe/Amsterdam");
        postRequest("invalid-study-guid", payload)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void createFew() {
        GovernedUserRegistrationPayload payload1 = new GovernedUserRegistrationPayload("it", "John", "Doe",
                "Europe/Amsterdam");
        GovernedUserRegistrationPayload payload2 = new GovernedUserRegistrationPayload("en", "Frank", "Johnson",
                "Europe/Moscow");
        List<String> governedUserGuids = new ArrayList<>();
        governedUserGuids.add(postRequest(testData.getStudyGuid(), payload1)
                .then().assertThat()
                .statusCode(200)
                .extract().path("ddpUserGuid"));

        governedUserGuids.add(postRequest(testData.getStudyGuid(), payload2)
                .then().assertThat()
                .statusCode(200)
                .extract().path("ddpUserGuid"));

        List<Governance> governances = TransactionWrapper.withTxn(handle -> handle.attach(UserGovernanceDao.class)
                .findActiveGovernancesByProxyAndStudyGuids(testData.getUserGuid(), testData.getStudyGuid())
                .collect(Collectors.toList()));
        for (Governance governance : governances) {
            assertTrue("There is unexpected governance in the list", governedUserGuids.contains(governance.getGovernedUserGuid()));
            governedUserGuids.remove(governance.getGovernedUserGuid());
        }
        assertTrue("Governed user is not found in the governances list", governedUserGuids.isEmpty());
    }

    private Response postRequest(String studyGuid, GovernedUserRegistrationPayload payload) {
        return given().auth().oauth2(token)
                .pathParam("userGuid", testData.getUserGuid())
                .pathParam("studyGuid", studyGuid)
                .body(payload, ObjectMapperType.GSON)
                .when().post(url);
    }
}
