package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.json.errors.ApiError;
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

public class PatchMedicalProviderRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(PatchMedicalProviderRouteTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;

    private static void insertTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(MedicalProviderDao.class).insert(
                            new MedicalProviderDto(
                                    null,
                                    TestMedicalProviderData.GUID,
                                    testData.getUserId(),
                                    testData.getStudyId(),
                                    TestMedicalProviderData.INSTITUTION_TYPE,
                                    TestMedicalProviderData.INSTITUTION_NAME,
                                    TestMedicalProviderData.PHYSICIAN_NAME,
                                    TestMedicalProviderData.CITY,
                                    TestMedicalProviderData.STATE,
                                    null,
                                    null,
                                    null,
                                    null
                            )
                    );
                }
        );
    }

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
        url = RouteTestUtil.getTestingBaseUrl() + API.USER_MEDICAL_PROVIDER;
        insertTestData();
    }

    @AfterClass
    public static void teardown() {
        deleteTestData();
    }

    @After
    public void removeEnrollment() {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.deleteEnrollmentStatus(handle, testData));
    }

    @Before
    public void setupEnrollment() {
        TransactionWrapper.useTxn(handle ->
                TestDataSetupUtil.setUserEnrollmentStatus(handle, testData, EnrollmentStatusType.REGISTERED));

    }

    @Test
    public void testPatchMedicalProvider_200UserHasCompletedStudy() throws Exception {
        String payload = "{\"physicianName\": \"" + TestMedicalProviderData.PHYSICIAN_NAME.toUpperCase() + "\""
                + ", \"state\": null }";

        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(testData.getUserGuid(),
                    testData.getStudyGuid(),
                    EnrollmentStatusType.ENROLLED);
        });

        Thread.sleep(500); // This is because EligibilityInclusion is >=

        long timeBeforeSecondEntry = Instant.now().toEpochMilli();

        Request request = RouteTestUtil.buildAuthorizedPatchRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.MEDICAL_PROVIDER_GUID, TestMedicalProviderData.GUID)
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.UPDATED_INSTITUTION_URL_COMPONENT),
                payload
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(204, response.getStatusLine().getStatusCode());
        Optional<MedicalProviderDto> medicalProviderDtoOpt = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiMedicalProvider.class).getByGuid(TestMedicalProviderData.GUID)
        );
        assertTrue(medicalProviderDtoOpt.isPresent());
        MedicalProviderDto medicalProviderDto = medicalProviderDtoOpt.get();
        Assert.assertEquals(TestMedicalProviderData.GUID, medicalProviderDto.getUserMedicalProviderGuid());
        // Checking that the fields omitted in JSON RETAINED their values
        Assert.assertEquals(TestMedicalProviderData.INSTITUTION_NAME, medicalProviderDto.getInstitutionName());
        Assert.assertEquals(TestMedicalProviderData.CITY, medicalProviderDto.getCity());
        // Checking that the field specified in JSON CHANGED its value
        Assert.assertEquals(TestMedicalProviderData.PHYSICIAN_NAME.toUpperCase(), medicalProviderDto.getPhysicianName());
        // Checking that the field explicitly nulled in JSON WAS NULLED
        Assert.assertNull(medicalProviderDto.getState());

        List<Long> resultList = TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                .findByStudyGuidAfterOrEqualToInstant(testData.getStudyGuid(), timeBeforeSecondEntry))
                .stream()
                .map(obj -> obj.getUserId())
                .collect(Collectors.toList());


        assertEquals(resultList.size(), 1);
        assertTrue(resultList.contains(testData.getUserId()));

        Assert.assertEquals(
                TestMedicalProviderData.UPDATED_INSTITUTION_TYPE,
                medicalProviderDto.getInstitutionType()
        );
    }

    @Test
    public void testPatchMedicalProvider_200UserHasNotCompletedStudy() throws Exception {
        String payload = "{\"physicianName\": \"" + TestMedicalProviderData.PHYSICIAN_NAME.toUpperCase() + "\""
                + ", \"state\": null }";
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.MEDICAL_PROVIDER_GUID, TestMedicalProviderData.GUID)
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.UPDATED_INSTITUTION_URL_COMPONENT),
                payload
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(204, response.getStatusLine().getStatusCode());
        Optional<MedicalProviderDto> medicalProviderDtoOpt = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiMedicalProvider.class).getByGuid(TestMedicalProviderData.GUID)
        );
        assertTrue(medicalProviderDtoOpt.isPresent());
        MedicalProviderDto medicalProviderDto = medicalProviderDtoOpt.get();
        Assert.assertEquals(TestMedicalProviderData.GUID, medicalProviderDto.getUserMedicalProviderGuid());
        // Checking that the fields omitted in JSON RETAINED their values
        Assert.assertEquals(TestMedicalProviderData.INSTITUTION_NAME, medicalProviderDto.getInstitutionName());
        Assert.assertEquals(TestMedicalProviderData.CITY, medicalProviderDto.getCity());
        // Checking that the field specified in JSON CHANGED its value
        Assert.assertEquals(TestMedicalProviderData.PHYSICIAN_NAME.toUpperCase(), medicalProviderDto.getPhysicianName());
        // Checking that the field explicitly nulled in JSON WAS NULLED
        Assert.assertNull(medicalProviderDto.getState());

        Assert.assertTrue(TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyGuids(testData.getUserGuid(),
                        testData.getStudyGuid()).get() == EnrollmentStatusType.REGISTERED));

        Assert.assertEquals(
                TestMedicalProviderData.UPDATED_INSTITUTION_TYPE,
                medicalProviderDto.getInstitutionType()
        );
    }

    @Test
    public void testPatchMedicalProvider_404_noSuchMedicalProvider() throws Exception {
        PostPatchMedicalProviderRequestPayload payload = new PostPatchMedicalProviderRequestPayload(
                TestMedicalProviderData.INSTITUTION_NAME,
                TestMedicalProviderData.PHYSICIAN_NAME.toUpperCase(),
                TestMedicalProviderData.CITY,
                TestMedicalProviderData.STATE
        );
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.MEDICAL_PROVIDER_GUID, "UNKNOWNPHYS")
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.UPDATED_INSTITUTION_URL_COMPONENT),
                new Gson().toJson(payload)
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        ApiError apiError = new Gson()
                .fromJson(json, ApiError.class);
        Assert.assertEquals(apiError.getCode(), ErrorCodes.NOT_FOUND);
        String errMsg = "A medical provider with GUID UNKNOWNPHYS you try to update is not found";
        Assert.assertEquals(apiError.getMessage(), errMsg);
    }

    @Test
    public void testPatchMedicalProvider_400_badPayload() throws Exception {
        Request request = RouteTestUtil.buildAuthorizedPatchRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.MEDICAL_PROVIDER_GUID, TestMedicalProviderData.GUID)
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.UPDATED_INSTITUTION_URL_COMPONENT),
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
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
        public static final InstitutionType UPDATED_INSTITUTION_TYPE = InstitutionType.PHYSICIAN;
        public static final String INSTITUTION_NAME = "Princeton-Plainsboro Teaching Hospital";
        public static final String PHYSICIAN_NAME = "House MD";
        public static final String CITY = "West Windsor Township";
        public static final String STATE = "New Jersey";

        public static final String INSTITUTION_URL_COMPONENT = "institution";
        public static final String UPDATED_INSTITUTION_URL_COMPONENT = "physician";
    }
}
