package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiStudyPdfMapping;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.model.dsm.PdfMappingType;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;
import org.broadinstitute.ddp.util.PdfTestingUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetPdfRouteTest extends DsmRouteTest {

    private static final Logger LOG = LoggerFactory.getLogger(GetPdfRouteTest.class);
    private static String answerText = UUID.randomUUID().toString();
    private static String configurationName = UUID.randomUUID().toString();
    private static String pdfFilename = UUID.randomUUID().toString();
    private static String url;
    private static PdfTestingUtil.PdfDbInfo pdfDbInfo;
    private static String legacyAltPid = "12345.GUID-GUID-GUID";
    private static TestDataSetupUtil.GeneratedTestData secondStudy;
    private static TestDataSetupUtil.GeneratedTestData thirdStudy;

    @BeforeClass
    public static void setupClass() throws Exception {
        String endpoint = API.DSM_PARTICIPANT_PDF
                .replace(PathParam.STUDY_GUID, "{studyGuid}")
                .replace(PathParam.CONFIG_NAME, "{configName}")
                .replace(PathParam.USER_GUID, "{userGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;

        // Preparing test data. All heavy lifting is done by this method
        pdfDbInfo = PdfTestingUtil.insertTestPdfWithConfiguration(
                PdfMappingType.CONSENT,
                //null,
                generatedTestData.getUserId(),
                generatedTestData.getTestingUser().getUserGuid(),
                generatedTestData.getStudyId(),
                generatedTestData.getStudyGuid(),
                configurationName,
                pdfFilename,
                answerText
        );

        TransactionWrapper.useTxn(handle -> {
            secondStudy = TestDataSetupUtil.generateBasicUserTestData(handle);
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("guid", userGuid)
                    .execute());

            thirdStudy = TestDataSetupUtil.generateBasicUserTestData(handle);

            PdfService pdfService = new PdfService();
            PdfConfiguration pdfConfiguration = pdfService.findFullConfigForUser(
                    handle, pdfDbInfo.pdfConfigId(), generatedTestData.getUserGuid(), generatedTestData.getStudyGuid());
            pdfService.generateAndUpload(
                    handle,
                    new PdfGenerationService(),
                    new PdfBucketService(RouteTestUtil.getConfig()),
                    pdfConfiguration,
                    generatedTestData.getUserGuid(),
                    generatedTestData.getStudyGuid());
        });
    }

    @AfterClass
    public static void removeTestData() {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.attach(JdbiStudyPdfMapping.class).deleteById(pdfDbInfo.mappingId()));
            assertEquals(1, handle.attach(PdfDao.class).deleteAllConfigVersions(pdfDbInfo.pdfConfigId()));
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = null where guid = :guid")
                    .bind("guid", userGuid)
                    .execute());
        });
    }

    private void removeEnrollmentForUserAndStudy(Handle handle, String studyGuid, String userGuid) {
        handle.attach(JdbiUserStudyEnrollment.class).deleteByUserGuidStudyGuid(userGuid, studyGuid);
    }


    @Test
    public void test_givenPdfConfigExists_whenEndpointCalled_thenFilledPdfReturned() throws IOException {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED)
        );

        byte[] actualBytes = given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", generatedTestData.getStudyGuid())
                .pathParam("userGuid", generatedTestData.getTestingUser().getUserGuid())
                .pathParam("configName", configurationName)
                .when()
                .get(url).then().assertThat()
                .statusCode(HttpStatus.SC_OK)
                .contentType("application/pdf")
                .header("Content-Disposition", equalTo(String.format("inline; filename=\"%s.pdf\"", pdfFilename)))
                .and()
                .extract()
                .response()
                .asByteArray();

        PdfDocument actualPdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(actualBytes)));
        assertEquals(1, actualPdf.getNumberOfPages());

        String actualText = PdfTextExtractor.getTextFromPage(actualPdf.getPage(1));
        assertTrue(actualText.contains(answerText));

        TransactionWrapper.useTxn(handle ->
                removeEnrollmentForUserAndStudy(handle, generatedTestData.getStudyGuid(), generatedTestData.getUserGuid())
        );
    }

    @Test
    public void test_givenConsentExists_whenEndpointCalledWithLegacyAltpid_then200Returned() throws Exception {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED)
        );

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", generatedTestData.getStudyGuid())
                .pathParam("userGuid", generatedTestData.getTestingUser().getUserGuid())
                .pathParam("configName", configurationName)
                .when()
                .get(url).then().assertThat()
                .statusCode(HttpStatus.SC_OK);

        TransactionWrapper.useTxn(handle ->
                removeEnrollmentForUserAndStudy(handle, generatedTestData.getStudyGuid(), generatedTestData.getUserGuid())
        );
    }

    @Test
    public void test_givenUserEnrolledButNowSuspended_thenReturns200() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED);
            TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.CONSENT_SUSPENDED);
        });

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", generatedTestData.getStudyGuid())
                .pathParam("userGuid", generatedTestData.getUserGuid())
                .pathParam("configName", configurationName)
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_OK);

        TransactionWrapper.useTxn(handle ->
                removeEnrollmentForUserAndStudy(handle, generatedTestData.getStudyGuid(), generatedTestData.getUserGuid())
        );
    }

    @Test
    public void test_givenUserDoesntExist_whenEndpointIsCalled_thenItReturns404_andBodyConstainsErrorExplanation() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", generatedTestData.getStudyGuid())
                .pathParam("userGuid", "non-existent-user")
                .pathParam("configName", configurationName)
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.USER_NOT_FOUND))
                .body("message", containsString("non-existent-user"));
    }

    @Test
    public void test_givenStudyDoesntExist_whenEndpointIsCalled_thenItReturns404_andBodyConstainsErrorExplanation() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", "not-existent-study")
                .pathParam("userGuid", generatedTestData.getTestingUser().getUserGuid())
                .pathParam("configName", configurationName)
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.STUDY_NOT_FOUND))
                .body("message", containsString("not-existent-study"));
    }

    @Test
    public void test_givenPdfConfiNameDoesntExist_whenEndpointIsCalled_thenItReturns404_andBodyConstainsErrorExplanation() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", generatedTestData.getStudyGuid())
                .pathParam("userGuid", generatedTestData.getTestingUser().getUserGuid())
                .pathParam("configName", "not-existent-config-name")
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.PDF_CONFIG_NAME_NOT_FOUND))
                .body("message", containsString("not-existent-config-name"));
    }

    @Test
    public void test_givenStudyDoesExistUserNotEnrolled_whenEndpointIsCalled_thenItReturns500_andBodyConstainsErrorExplanation() {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.REGISTERED)
        );

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", generatedTestData.getStudyGuid())
                .pathParam("userGuid", generatedTestData.getUserGuid())
                .pathParam("configName", configurationName)
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.UNSATISFIED_PRECONDITION))
                .body("message", containsString(thirdStudy.getUserGuid()));

        TransactionWrapper.useTxn(handle ->
                removeEnrollmentForUserAndStudy(handle, generatedTestData.getStudyGuid(), generatedTestData.getUserGuid())
        );
    }

    @Test
    public void test_givenStudyDoesExistUserDoesNotExist_whenEndpointIsCalled_thenItReturns404_andBodyConstainsErrorExplanation() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", thirdStudy.getStudyGuid())
                .pathParam("userGuid", "invalid-user")
                .pathParam("configName", configurationName)
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.USER_NOT_FOUND))
                .body("message", containsString("invalid-user"));
    }

}
