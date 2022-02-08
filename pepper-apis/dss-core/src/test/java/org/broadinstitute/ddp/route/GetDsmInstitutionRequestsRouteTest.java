package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.restassured.http.ContentType;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.dsm.InstitutionRequests;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GetDsmInstitutionRequestsRouteTest extends DsmRouteTest {
    String legacyAltPid = "GUID.GUID.GUID.11111";

    @Before
    public void testDataSetup() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.setUserEnrollmentStatus(handle, generatedTestData, EnrollmentStatusType.ENROLLED);
            TestDataSetupUtil.createTestMedicalProvider(handle, generatedTestData);
        });
    }

    public void removeAltPid(Handle handle) {
        handle.createUpdate("update user set legacy_altpid = null where guid = :guid")
                .bind("guid", userGuid)
                .execute();
    }

    @After
    public void testDataCleanup() {
        TransactionWrapper.useTxn(handle -> {
            removeAltPid(handle);
            TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData);
        });
    }

    @Test
    public void testHappyPathGetAll() throws IOException {
        TransactionWrapper.useTxn(handle -> removeAltPid(handle));
        InstitutionRequests requests = getInstitutionRequests();

        assertEquals(requests.getUserGuid(), generatedTestData.getUserGuid());
        assertEquals(requests.getInstitutions().size(), 1);
        assertEquals(requests.getInstitutions().get(0).getInstitutionId(),
                generatedTestData.getMedicalProvider().getUserMedicalProviderGuid());

        assertEquals(requests.getInstitutions().get(0).getInstitutionType(),
                generatedTestData.getMedicalProvider().getInstitutionType());
    }

    @Test
    public void testHappyPathGetAllReturnAltPid() throws IOException {
        TransactionWrapper.useTxn(handle -> {
            assertEquals(1, handle.createUpdate("update user set legacy_altpid = :legacyAltPid where guid = :guid")
                    .bind("legacyAltPid", legacyAltPid)
                    .bind("guid", userGuid)
                    .execute());
        });
        InstitutionRequests requests = getInstitutionRequests();

        assertEquals(requests.getUserGuid(), legacyAltPid);
        assertEquals(requests.getInstitutions().size(), 1);
        assertEquals(requests.getInstitutions().get(0).getInstitutionId(),
                generatedTestData.getMedicalProvider().getUserMedicalProviderGuid());

        assertEquals(requests.getInstitutions().get(0).getInstitutionType(),
                generatedTestData.getMedicalProvider().getInstitutionType());
    }

    public InstitutionRequests getInstitutionRequests() throws IOException {
        String requestUrl = RouteTestUtil.getTestingBaseUrl()
                + RouteConstants.API.DSM_GET_INSTITUTION_REQUESTS
                .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid())
                .replace(RouteConstants.PathParam.MAX_ID, "0");


        Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();

        String json = EntityUtils.toString(res.returnResponse().getEntity());
        Type collectionType = new TypeToken<Collection<InstitutionRequests>>() {
        }.getType();
        Collection<InstitutionRequests> institutionRequests =
                new Gson().fromJson(json, collectionType);
        assertEquals(institutionRequests.size(), 1);


        return (InstitutionRequests) institutionRequests.toArray()[0];
    }

    @Test
    public void testHappyPathGetAllSkipUsersNoInstitutions() throws IOException {
        TransactionWrapper.useTxn(handle -> TestDataSetupUtil.deleteTestMedicalProvider(handle, generatedTestData));
        try {
            String requestUrl = RouteTestUtil.getTestingBaseUrl()
                    + RouteConstants.API.DSM_GET_INSTITUTION_REQUESTS
                    .replace(RouteConstants.PathParam.STUDY_GUID, generatedTestData.getStudyGuid())
                    .replace(RouteConstants.PathParam.MAX_ID, "0");


            Response res = RouteTestUtil.buildAuthorizedGetRequest(dsmClientAccessToken, requestUrl).execute();

            String json = EntityUtils.toString(res.returnResponse().getEntity());
            Type collectionType = new TypeToken<Collection<InstitutionRequests>>() {
            }.getType();
            Collection<InstitutionRequests> institutionRequests =
                    new Gson().fromJson(json, collectionType);

            assertEquals(0, institutionRequests.size());
        } finally {
            TransactionWrapper.useTxn(handle -> TestDataSetupUtil.createTestMedicalProvider(handle, generatedTestData));
        }
    }

    @Test
    public void test_whenGivenTimestampIndicatingNoNewInstitutions_returnsEmptyList() {
        String url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.DSM_GET_INSTITUTION_REQUESTS
                .replace(RouteConstants.PathParam.STUDY_GUID, "{studyGuid}")
                .replace(RouteConstants.PathParam.MAX_ID, "{maxId}");

        // Make a timestamp in the future.
        long timestamp = Instant.now().getEpochSecond() + 1000;

        given().auth().oauth2(dsmClientAccessToken)
                .pathParam("studyGuid", generatedTestData.getStudyGuid())
                .pathParam("maxId", timestamp)
                .when().get(url)
                .then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("$.size()", equalTo(0));
    }
}
