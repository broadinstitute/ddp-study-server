package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.medicalprovider.PostMedicalProviderResponsePayload;
import org.broadinstitute.ddp.json.medicalprovider.PostPatchMedicalProviderRequestPayload;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostMedicalProviderRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(PostMedicalProviderRouteTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;

    private static void deleteTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiMedicalProvider.class).deleteByGuid(TestMedicalProviderData.GUID);
                }
        );
    }

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        token = testData.getTestingUser().getToken();
        url = RouteTestUtil.getTestingBaseUrl() + API.USER_MEDICAL_PROVIDERS;
    }

    @Before
    public void setupEnrollment() {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, testData, EnrollmentStatusType.REGISTERED));
    }

    @After
    public void removeEnrollment() {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.deleteEnrollmentStatus(handle, testData));
    }

    @AfterClass
    public static void teardown() {
        deleteTestData();
    }

    @Test
    public void testPostMedicalProvider_createContentNotRequired() throws Exception {
        Request request = RouteTestUtil.buildAuthorizedPostRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT),
                null
        );

        HttpResponse response = request.execute().returnResponse();
        assertEquals(HttpStatus.SC_CREATED, response.getStatusLine().getStatusCode());

        String json = EntityUtils.toString(response.getEntity());
        PostMedicalProviderResponsePayload respPayload = new Gson().fromJson(json, PostMedicalProviderResponsePayload.class);
        assertNotNull(respPayload);
    }

    @Test
    public void testPostMedicalProvider_201UserHasNotCompletedStudy() throws Exception {
        PostPatchMedicalProviderRequestPayload payload = new PostPatchMedicalProviderRequestPayload(
                TestMedicalProviderData.INSTITUTION_NAME,
                TestMedicalProviderData.PHYSICIAN_NAME,
                TestMedicalProviderData.CITY,
                TestMedicalProviderData.STATE
        );
        Request request = RouteTestUtil.buildAuthorizedPostRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT),
                new Gson().toJson(payload)
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(201, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        PostMedicalProviderResponsePayload respPayload = new Gson().fromJson(json, PostMedicalProviderResponsePayload.class);

        Optional<MedicalProviderDto> medicalProviderDtoOpt = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiMedicalProvider.class).getByGuid(respPayload.getMedicalProviderGuid())
        );
        Assert.assertTrue(medicalProviderDtoOpt.isPresent());
        MedicalProviderDto medicalProviderDto = medicalProviderDtoOpt.get();

        Assert.assertEquals(respPayload.getMedicalProviderGuid(), medicalProviderDto.getUserMedicalProviderGuid());
        Assert.assertEquals(TestMedicalProviderData.INSTITUTION_NAME, medicalProviderDto.getInstitutionName());
        Assert.assertEquals(TestMedicalProviderData.PHYSICIAN_NAME, medicalProviderDto.getPhysicianName());
        Assert.assertEquals(TestMedicalProviderData.CITY, medicalProviderDto.getCity());
        Assert.assertEquals(TestMedicalProviderData.STATE, medicalProviderDto.getState());
        Assert.assertEquals(TestMedicalProviderData.INSTITUTION_TYPE, medicalProviderDto.getInstitutionType());

        Assert.assertTrue(TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyGuids(testData.getUserGuid(),
                        testData.getStudyGuid()).get() == EnrollmentStatusType.REGISTERED));
    }

    @Test
    public void testPostMedicalProvider_201UserHasCompletedStudy() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(testData.getUserGuid(),
                    testData.getStudyGuid(),
                    EnrollmentStatusType.ENROLLED);
        });

        Thread.sleep(500); // This is because EligibilityInclusion is >=

        long timeBeforeSecondEntry = Instant.now().toEpochMilli();

        PostPatchMedicalProviderRequestPayload payload = new PostPatchMedicalProviderRequestPayload(
                TestMedicalProviderData.INSTITUTION_NAME,
                TestMedicalProviderData.PHYSICIAN_NAME,
                TestMedicalProviderData.CITY,
                TestMedicalProviderData.STATE
        );
        Request request = RouteTestUtil.buildAuthorizedPostRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT),
                new Gson().toJson(payload)
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(201, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        PostMedicalProviderResponsePayload respPayload = new Gson().fromJson(json, PostMedicalProviderResponsePayload.class);

        Optional<MedicalProviderDto> medicalProviderDtoOpt = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiMedicalProvider.class).getByGuid(respPayload.getMedicalProviderGuid())
        );
        Assert.assertTrue(medicalProviderDtoOpt.isPresent());
        MedicalProviderDto medicalProviderDto = medicalProviderDtoOpt.get();

        Assert.assertEquals(respPayload.getMedicalProviderGuid(), medicalProviderDto.getUserMedicalProviderGuid());
        Assert.assertEquals(TestMedicalProviderData.INSTITUTION_NAME, medicalProviderDto.getInstitutionName());
        Assert.assertEquals(TestMedicalProviderData.PHYSICIAN_NAME, medicalProviderDto.getPhysicianName());
        Assert.assertEquals(TestMedicalProviderData.CITY, medicalProviderDto.getCity());
        Assert.assertEquals(TestMedicalProviderData.STATE, medicalProviderDto.getState());
        Assert.assertEquals(TestMedicalProviderData.INSTITUTION_TYPE, medicalProviderDto.getInstitutionType());

        List<Long> resultList = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                .findByStudyGuidAfterOrEqualToInstant(testData.getStudyGuid(), timeBeforeSecondEntry))
                .stream()
                .map(obj -> obj.getUserId())
                .collect(Collectors.toList());


        assertTrue(resultList.size() == 1);
        assertTrue(resultList.contains(testData.getUserId()));
    }

    @Test
    public void testPostMedicalProvider_CompletedStudyBlankMedicalProvider() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(testData.getUserGuid(),
                    testData.getStudyGuid(),
                    EnrollmentStatusType.ENROLLED);
        });

        Thread.sleep(500); // This is because EligibilityInclusion is >=

        long timeBeforeSecondEntry = Instant.now().toEpochMilli();

        PostPatchMedicalProviderRequestPayload payload = new PostPatchMedicalProviderRequestPayload(
                null,
                null,
                null,
                null
        );
        Request request = RouteTestUtil.buildAuthorizedPostRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT),
                new Gson().toJson(payload)
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(201, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        PostMedicalProviderResponsePayload respPayload = new Gson().fromJson(json, PostMedicalProviderResponsePayload.class);

        Optional<MedicalProviderDto> medicalProviderDtoOpt = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiMedicalProvider.class).getByGuid(respPayload.getMedicalProviderGuid())
        );
        Assert.assertTrue(medicalProviderDtoOpt.isPresent());
        MedicalProviderDto medicalProviderDto = medicalProviderDtoOpt.get();

        List<Long> resultList = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                .findByStudyGuidAfterOrEqualToInstant(testData.getStudyGuid(), timeBeforeSecondEntry))
                .stream()
                .map(obj -> obj.getUserId())
                .collect(Collectors.toList());


        assertTrue(resultList.size() == 0);
    }

    @Test
    public void testPostMedicalProvider_401_nonExistentUser() throws Exception {
        PostPatchMedicalProviderRequestPayload payload = new PostPatchMedicalProviderRequestPayload(
                TestMedicalProviderData.INSTITUTION_NAME,
                TestMedicalProviderData.PHYSICIAN_NAME,
                TestMedicalProviderData.CITY,
                TestMedicalProviderData.STATE
        );
        Request request = RouteTestUtil.buildAuthorizedPostRequest(
                token,
                url.replace(PathParam.USER_GUID, "UNKNOWNUSR")
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT),
                new Gson().toJson(payload)
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(401, response.getStatusLine().getStatusCode());
    }

    @Test
    public void testPostMedicalProvider_400_badPayload() throws Exception {
        Request request = RouteTestUtil.buildAuthorizedPostRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT),
                "[}"
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(400, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        ApiError apiError = new Gson()
                .fromJson(json, ApiError.class);
        Assert.assertEquals(apiError.getCode(), ErrorCodes.BAD_PAYLOAD);
    }

    private static final class TestMedicalProviderData {
        public static final String GUID = "AABBCCDD97";
        public static final String INSTITUTION_NAME = "Princeton-Plainsboro Teaching Hospital";
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
        public static final String PHYSICIAN_NAME = "House MD";
        public static final String CITY = "West Windsor Township";
        public static final String STATE = "New Jersey";

        public static final String INSTITUTION_URL_COMPONENT = "institution";
    }
}
