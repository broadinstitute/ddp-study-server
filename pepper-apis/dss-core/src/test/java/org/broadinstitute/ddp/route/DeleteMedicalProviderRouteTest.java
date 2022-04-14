package org.broadinstitute.ddp.route;

import java.util.Optional;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteMedicalProviderRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteMedicalProviderRouteTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;

    private static void insertTestData() {
        TransactionWrapper.useTxn(
                handle -> {
                    handle.attach(JdbiMedicalProvider.class).insert(
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
    public static void setup() throws Exception {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        token = testData.getTestingUser().getToken();
        url = RouteTestUtil.getTestingBaseUrl() + API.USER_MEDICAL_PROVIDER;
        insertTestData();
    }

    @AfterClass
    public static void teardown() {
        deleteTestData();
    }

    @Test
    public void testDeleteMedicalProvider_200() throws Exception {
        Request request = RouteTestUtil.buildAuthorizedDeleteRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.MEDICAL_PROVIDER_GUID, TestMedicalProviderData.GUID)
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT)
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(204, response.getStatusLine().getStatusCode());
        Optional<MedicalProviderDto> medicalProviderDtoOpt = TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiMedicalProvider.class).getByGuid(TestMedicalProviderData.GUID)
        );
        Assert.assertFalse(medicalProviderDtoOpt.isPresent());
    }

    @Test
    public void testDeleteMedicalProvider_404_noSuchMedicalProvider() throws Exception {
        Request request = RouteTestUtil.buildAuthorizedDeleteRequest(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.MEDICAL_PROVIDER_GUID, "UNKNOWNPHYS")
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT)
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(404, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        ApiError apiError = new Gson()
                .fromJson(json, ApiError.class);
        Assert.assertEquals(apiError.getCode(), ErrorCodes.NOT_FOUND);
        String errMsg = "A medical provider with GUID UNKNOWNPHYS you try to delete is not found";
        Assert.assertEquals(apiError.getMessage(), errMsg);
    }

    private static final class TestMedicalProviderData {
        public static final String GUID = "AABBCCDD97";
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
        public static final String INSTITUTION_NAME = "Princeton-Plainsboro Teaching Hospital";
        public static final String PHYSICIAN_NAME = "House MD";
        public static final String CITY = "West Windsor Township";
        public static final String STATE = "New Jersey";

        public static final String INSTITUTION_URL_COMPONENT = "institution";
    }
}
