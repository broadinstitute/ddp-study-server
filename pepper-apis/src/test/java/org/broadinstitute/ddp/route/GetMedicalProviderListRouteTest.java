package org.broadinstitute.ddp.route;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.json.medicalprovider.GetMedicalProviderResponse;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetMedicalProviderListRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetMedicalProviderListRouteTest.class);

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
                    handle.attach(JdbiMedicalProvider.class).insert(
                            new MedicalProviderDto(
                                    null,
                                    TestMedicalProviderData.GUID2,
                                    testData.getUserId(),
                                    testData.getStudyId(),
                                    TestMedicalProviderData.PHYSICIAN_INSTITUTION_TYPE,
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
                    handle.attach(JdbiMedicalProvider.class).deleteByGuid(TestMedicalProviderData.GUID2);
                }
        );
    }

    private List<GetMedicalProviderResponse> executeGetRequestAndGetResponseBody(
            String token,
            String url
    ) throws IOException {
        Request request = RouteTestUtil.buildAuthorizedGetRequest(token, url);
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        String json = EntityUtils.toString(response.getEntity());
        return new Gson().fromJson(json, new TypeToken<List<GetMedicalProviderResponse>>() {
        }.getType());
    }

    private GetMedicalProviderResponse extractTestMedicalProvider(List<GetMedicalProviderResponse> medicalProviders) {
        List<GetMedicalProviderResponse> testMedicalProviders = medicalProviders.stream()
                .filter(p -> p.getMedicalProviderGuid().equals(TestMedicalProviderData.GUID))
                .collect(Collectors.toList());
        Assert.assertTrue(testMedicalProviders.size() == 1);
        return testMedicalProviders.get(0);
    }

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        token = testData.getTestingUser().getToken();
        url = RouteTestUtil.getTestingBaseUrl() + API.USER_MEDICAL_PROVIDERS;
        insertTestData();
    }

    @AfterClass
    public static void teardown() {
        deleteTestData();
    }

    @Test
    public void testGetMedicalProvidersList_200() throws Exception {
        List<GetMedicalProviderResponse> medicalProviders = executeGetRequestAndGetResponseBody(
                token,
                url.replace(PathParam.USER_GUID, testData.getUserGuid())
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT)
        );
        Assert.assertNotNull(medicalProviders);
        GetMedicalProviderResponse testMedicalProvider = extractTestMedicalProvider(medicalProviders);
        Assert.assertEquals(TestMedicalProviderData.GUID, testMedicalProvider.getMedicalProviderGuid());
        Assert.assertEquals(TestMedicalProviderData.INSTITUTION_NAME, testMedicalProvider.getInstitutionName());
        Assert.assertEquals(TestMedicalProviderData.PHYSICIAN_NAME, testMedicalProvider.getPhysicianName());
        Assert.assertEquals(TestMedicalProviderData.CITY, testMedicalProvider.getCity());
        Assert.assertEquals(TestMedicalProviderData.STATE, testMedicalProvider.getState());
    }

    @Test
    public void testGetMedicalProvidersList_401_nonExistentUser() throws Exception {
        Request request = RouteTestUtil.buildAuthorizedGetRequest(
                token,
                url.replace(PathParam.USER_GUID, "UNKNOWNUSR")
                        .replace(PathParam.STUDY_GUID, testData.getStudyGuid())
                        .replace(PathParam.INSTITUTION_TYPE, TestMedicalProviderData.INSTITUTION_URL_COMPONENT)
        );
        HttpResponse response = request.execute().returnResponse();
        Assert.assertEquals(401, response.getStatusLine().getStatusCode());
    }

    private static final class TestMedicalProviderData {
        public static final String GUID = "AABBCCDD97";
        public static final String GUID2 = "00BBCCDD17";
        public static final InstitutionType INSTITUTION_TYPE = InstitutionType.INSTITUTION;
        public static final InstitutionType PHYSICIAN_INSTITUTION_TYPE = InstitutionType.PHYSICIAN;
        public static final String INSTITUTION_NAME = "Princeton-Plainsboro Teaching Hospital";
        public static final String PHYSICIAN_NAME = "House MD";
        public static final String CITY = "West Windsor Township";
        public static final String STATE = "New Jersey";

        public static final String INSTITUTION_URL_COMPONENT = "institution";
    }
}
