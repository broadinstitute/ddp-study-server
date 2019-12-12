package org.broadinstitute.ddp.route;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import com.google.gson.Gson;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.dsm.ParticipantStatus;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.DsmParticipantStatusService;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import org.mockserver.junit.MockServerRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetDsmParticipantStatusRouteTest extends IntegrationTestSuite.TestCase {
    private static final Logger LOG = LoggerFactory.getLogger(GetDsmParticipantStatusRouteTest.class);
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static long enrollmentStatusId;
    private static final String URL_REGEX = "^/info/participantstatus/.+";
    private static int mockserverPort;

    private static String token;
    private static String urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.PARTICIPANT_STATUS;

    private static DsmParticipantStatusService service;

    private void callRouteAndCheckStatus(String studyGuid, String userGuid, int expectedStatus) {
        String url = urlTemplate.replace(RouteConstants.PathParam.USER_GUID, userGuid)
                .replace(RouteConstants.PathParam.STUDY_GUID, studyGuid);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(expectedStatus).contentType(ContentType.JSON);
    }

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, false, mockserverPort);

    @BeforeClass
    public static void setupClass() throws MalformedURLException {
        URL dsmBaseUrl = new URL(ConfigManager.getInstance().getConfig().getString(ConfigFile.DSM_BASE_URL));
        mockserverPort = dsmBaseUrl.getPort();
        service = new DsmParticipantStatusService(dsmBaseUrl);

        testData = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateBasicUserTestData(handle);
        });
        token = testData.getTestingUser().getToken();

        enrollmentStatusId = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                    testData.getUserGuid(),
                    testData.getStudyGuid(),
                    EnrollmentStatusType.ENROLLED
                )
        );
    }

    @AfterClass
    public static void teardownClass() {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).deleteById(enrollmentStatusId)
        );
    }

    @Test
    public void test_givenStudyAndUserExist_whenRouteIsCalled_thenItReturnsValidParticipantStatus() {
        TestUtil.stubMockServerForRequest(
                mockServerRule.getPort(), URL_REGEX, 200, new Gson().toJson(TestData.DSM_PARTICIPANT_STATUS_DTO)
        );
        String url = urlTemplate.replace(RouteConstants.PathParam.USER_GUID, testData.getUserGuid())
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    public void test_givenStudyIsBlank_whenRouteIsCalled_thenItReturns400() {
        callRouteAndCheckStatus(" ", testData.getUserGuid(), 400);
    }

    @Test
    public void test_givenStudyDoesntExist_whenRouteIsCalled_thenItReturns404withProperErrorJson() {
        TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 404, "");
        String url = urlTemplate.replace(RouteConstants.PathParam.USER_GUID, testData.getUserGuid())
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(404).contentType(ContentType.JSON);
    }

    @Test
    public void test_givenDsmReturnsClientErrorStatus_whenRouteIsCalled_thenItReturns500() {
        LOG.info("test_givenDsmReturnsClientErrorStatus_whenRouteIsCalled_thenItReturns500");
        TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 403, "");
        callRouteAndCheckStatus(testData.getStudyGuid(), testData.getUserGuid(), 500);
    }

    @Test
    public void test_givenDsmReturnsServerErrorStatus_whenRouteIsCalled_thenItReturns500() {
        LOG.info("test_givenDsmReturnsServerErrorStatus_whenRouteIsCalled_thenItReturns500");
        TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 502, "");
        callRouteAndCheckStatus(testData.getStudyGuid(), testData.getUserGuid(), 500);
    }

    private static class TestData {
        public static final ParticipantStatus DSM_PARTICIPANT_STATUS_DTO = new ParticipantStatus(
                TestData.MR_REQUESTED_TIMESTAMP,
                TestData.MR_RECEIVED_TIMESTAMP,
                TestData.TISSUE_REQUESTED_TIMESTAMP,
                TestData.TISSUE_SENT_TIMESTAMP,
                TestData.TISSUE_RECEIVED_TIMESTAMP,
                new ArrayList<>()
        );
        public static final long MR_REQUESTED_TIMESTAMP = 1547890240;
        public static final long MR_RECEIVED_TIMESTAMP = 1549890640;
        public static final long TISSUE_REQUESTED_TIMESTAMP = 1552309836;
        public static final long TISSUE_SENT_TIMESTAMP = 1553519436;
        public static final long TISSUE_RECEIVED_TIMESTAMP = 1556519825;
        public static final String DSM_TOKEN = "aabbcc";
    }
}
