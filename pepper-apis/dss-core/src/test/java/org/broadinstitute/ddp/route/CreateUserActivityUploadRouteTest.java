package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.json.CreateUserActivityUploadPayload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateUserActivityUploadRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static TestFormActivity act;
    private static ActivityInstanceDto instanceDto;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();

            act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .withFileQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            instanceDto = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(act.getDef().getActivityId(), testData.getUserGuid());

            String endpoint = RouteConstants.API.USER_ACTIVITY_UPLOADS
                    .replace(RouteConstants.PathParam.USER_GUID, testData.getUserGuid())
                    .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid())
                    .replace(RouteConstants.PathParam.INSTANCE_GUID, instanceDto.getGuid());
            url = RouteTestUtil.getTestingBaseUrl() + endpoint;
        });
    }

    @Test
    public void testInvalidInput() {
        var payload = new CreateUserActivityUploadPayload(null, "", 123, "mime", false);
        given().auth().oauth2(token)
                .body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void testFileSizeExceeded() {
        String stableId = act.getFileQuestion().getStableId();
        long largeFileSize = Long.MAX_VALUE;
        var payload = new CreateUserActivityUploadPayload(stableId, "file.pdf", largeFileSize, "application/pdf", false);
        given().auth().oauth2(token)
                .body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(400).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD));
    }

    @Test
    public void testNotFileQuestion() {
        String wrongStableId = act.getTextQuestion().getStableId();
        var payload = new CreateUserActivityUploadPayload(wrongStableId, "file.pdf", 123, "application/pdf", false);
        given().auth().oauth2(token)
                .body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(422).contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_SUPPORTED));
    }

    @Test
    public void testInstanceReadOnly() {
        TransactionWrapper.useTxn(handle -> assertEquals(1,
                handle.attach(JdbiActivityInstance.class)
                        .updateIsReadonlyByGuid(true, instanceDto.getGuid())));
        try {
            String stableId = act.getFileQuestion().getStableId();
            var payload = new CreateUserActivityUploadPayload(stableId, "file.pdf", 123, "application/pdf", false);
            given().auth().oauth2(token)
                    .body(payload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(422).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY));
        } finally {
            TransactionWrapper.useTxn(handle -> assertEquals(1,
                    handle.attach(JdbiActivityInstance.class)
                            .updateIsReadonlyByGuid(null, instanceDto.getGuid())));
        }
    }

    @Test
    public void testUploadAuthorized() {
        String stableId = act.getFileQuestion().getStableId();
        var payload = new CreateUserActivityUploadPayload(stableId, "file.pdf", 123, "application/pdf", false);

        String uploadGuid = given().auth().oauth2(token)
                .body(payload, ObjectMapperType.GSON)
                .when().post(url)
                .then().assertThat()
                .statusCode(201).contentType(ContentType.JSON)
                .body("uploadGuid", not(isEmptyOrNullString()))
                .body("uploadUrl", not(isEmptyOrNullString()))
                .extract().path("uploadGuid");

        TransactionWrapper.useTxn(handle -> {
            var actual = handle.attach(FileUploadDao.class).findByGuid(uploadGuid).orElse(null);
            assertNotNull(actual);
            assertNotNull(actual.getFileName());
            assertTrue(actual.getFileName().contains("file.pdf"));
            assertEquals(123, actual.getFileSize());
            assertEquals("application/pdf", actual.getMimeType());
            assertFalse(actual.isVerified());
        });
    }
}
