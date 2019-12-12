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

public class GetDsmReleasePdfRouteTest extends DsmRouteTest {

    private static final String answerText = UUID.randomUUID().toString();
    private static final String configurationName = UUID.randomUUID().toString();
    private static final String pdfFilename = UUID.randomUUID().toString();
    private static PdfTestingUtil.PdfDbInfo pdfDbInfo;
    private static String url;
    private static String legacyAltPid = "12345.GUID-GUID-GUID";
    private static TestDataSetupUtil.GeneratedTestData secondStudy;

    @BeforeClass
    public static void setupRouteTest() throws Exception {
        String endpoint = API.DSM_PARTICIPANT_RELEASE_PDF
                .replace(PathParam.STUDY_GUID, "{studyGuid}")
                .replace(PathParam.USER_GUID, "{userGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
        pdfDbInfo = PdfTestingUtil.insertTestPdfWithConfiguration(
                PdfMappingType.RELEASE,
                generatedTestData.getUserId(),
                generatedTestData.getUserGuid(),
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
    public static void cleanupTestData() {
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
    public void test_nonExistentStudy_returns404() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", "non-existent-study")
                .pathParam("userGuid", userGuid)
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.STUDY_NOT_FOUND))
                .body("message", containsString("non-existent-study"));
    }

    @Test
    public void test_nonExistentParticipant_returns404() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyGuid)
                .pathParam("userGuid", "foo-bar-user")
                .when().get(url)
                .then().assertThat()
                .statusCode(404).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.USER_NOT_FOUND))
                .body("message", containsString("foo-bar-user"));
    }

    @Test
    public void test_missingPdfMapping_returns500() {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, secondStudy, EnrollmentStatusType.ENROLLED)
        );

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", secondStudy.getStudyGuid())
                .pathParam("userGuid", secondStudy.getUserGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(500).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.SERVER_ERROR))
                .body("message", containsString("RELEASE pdf mapping"));

        TransactionWrapper.useTxn(handle ->
                removeEnrollmentForUserAndStudy(handle, secondStudy.getStudyGuid(), secondStudy.getUserGuid())
        );
    }

    @Test
    public void test_providing_legacyaltpid_returns200() {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED)
        );

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyGuid)
                .pathParam("userGuid", legacyAltPid)
                .when().get(url)
                .then().assertThat()
                .statusCode(200);

        TransactionWrapper.useTxn(handle ->
                removeEnrollmentForUserAndStudy(handle, generatedTestData.getStudyGuid(), generatedTestData.getUserGuid())
        );
    }

    @Test
    public void test_renderedPdfReturned_withParticipantAnswersAndExpectedHttpAttributes() throws IOException {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED)
        );

        byte[] actualBytes = given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", studyGuid)
                .pathParam("userGuid", userGuid)
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType("application/pdf")
                .header("Content-Disposition", equalTo(String.format("inline; filename=\"%s.pdf\"", pdfFilename)))
                .and().extract().response().asByteArray();

        PdfDocument actualPdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(actualBytes)));
        assertEquals(1, actualPdf.getNumberOfPages());

        String actualText = PdfTextExtractor.getTextFromPage(actualPdf.getPage(1)); // page number is 1-indexed
        assertTrue(actualText.contains(answerText));

        TransactionWrapper.useTxn(handle ->
                removeEnrollmentForUserAndStudy(handle, generatedTestData.getStudyGuid(), generatedTestData.getUserGuid())
        );
    }

    @Test
    public void test_givenStudyDoesExistUserNotEnrolled_whenEndpointIsCalled_thenItReturns500_andBodyConstainsErrorExplanation() {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, secondStudy, EnrollmentStatusType.REGISTERED)
        );

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", secondStudy.getStudyGuid())
                .pathParam("userGuid", secondStudy.getUserGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.UNSATISFIED_PRECONDITION))
                .body("message", containsString(secondStudy.getUserGuid()));

        TransactionWrapper.useTxn(handle ->
                removeEnrollmentForUserAndStudy(handle, secondStudy.getStudyGuid(), secondStudy.getUserGuid())
        );
    }

    @Test
    public void test_givenStudyDoesExistUserDoesNotExist_whenEndpointIsCalled_thenItReturns404_andBodyConstainsErrorExplanation() {
        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", secondStudy.getStudyGuid())
                .pathParam("userGuid", generatedTestData.getUserGuid())
                .when().get(url)
                .then().assertThat()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.USER_NOT_FOUND))
                .body("message", containsString(generatedTestData.getUserGuid()));
    }
}
