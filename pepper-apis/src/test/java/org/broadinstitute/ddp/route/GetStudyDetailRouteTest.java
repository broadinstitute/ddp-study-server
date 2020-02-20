package org.broadinstitute.ddp.route;

import io.restassured.RestAssured;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.junit.BeforeClass;
import org.junit.Test;

public class GetStudyDetailRouteTest extends IntegrationTestSuite.TestCase {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
        });
        String endpoint = RouteConstants.API.STUDY_DETAIL
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @Test
    public void test_givenValidStudyId_whenRouteIsCalled_thenItReturns200() {
        RestAssured.given().auth().oauth2(token)
                .when().get(url)
                .then().assertThat().statusCode(200);
    }
}
