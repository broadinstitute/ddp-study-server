package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.typesafe.config.Config;
import io.restassured.RestAssured;
import okhttp3.HttpUrl;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrella;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyI18n;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.broadinstitute.ddp.model.study.EnrollmentStatusCount;
import org.broadinstitute.ddp.model.study.StudySummary;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetStudiesRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetStudiesRouteTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Gson gson;
    private static String token;
    private static String umbrella;
    private static List<StudyDto> studyDtos;
    private static List<EnrollmentStatusCount> enrollmentStatusCounts;

    @BeforeClass
    public static void beforeClass() {
        Config cfg = RouteTestUtil.getConfig();
        gson = new Gson();

        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
            umbrella = handle.attach(JdbiUmbrella.class).findById(testData.getTestingStudy().getUmbrellaId()).get().getGuid();

            studyDtos = new ArrayList<>();
            studyDtos.add(testData.getTestingStudy());
            enrollmentStatusCounts = new ArrayList<>();
            enrollmentStatusCounts.add(
                    EnrollmentStatusCount.getEnrollmentStatusCountByEnrollments(
                            handle.attach(JdbiUserStudyEnrollment.class).findByStudyGuid(studyDtos.get(0).getGuid())
                    )
            );

            Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
            String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
            String mgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
            String mgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
            studyDtos.add(TestDataSetupUtil.generateTestStudy(handle, auth0Domain, mgmtClientId, mgmtSecret,
                    OLCPrecision.MEDIUM, true, testData.getTestingStudy().getUmbrellaId()));
            enrollmentStatusCounts.add(new EnrollmentStatusCount(0, 0));
        });
    }

    private String buildGetStudiesUrl(String umbrellaGuid) {
        String url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.STUDY_ALL;
        return HttpUrl.parse(url).newBuilder().addQueryParameter(RouteConstants.QueryParam.UMBRELLA, umbrellaGuid).build().toString();
    }

    @Test
    public void test_givenStudyExists_andIsTranslated_whenRouteIsCalled_thenItReturns200_andNameIsTranslated() {
        long translationId = TransactionWrapper.withTxn(handle -> {
            long languageCodeId = LanguageStore.getOrComputeDefault(handle).getId();
            return handle.attach(JdbiUmbrellaStudyI18n.class).insert(
                    testData.getStudyId(),
                    languageCodeId,
                    "Test study #1",
                    null
            );
        });
        try {
            RestAssured.given().auth().oauth2(token)
                    .when().get(buildGetStudiesUrl(umbrella))
                    .then().assertThat().statusCode(200)
                    .body("[0].name", Matchers.is(Matchers.notNullValue()))
                    .body("[0].name", Matchers.is("Test study #1"));
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiUmbrellaStudyI18n.class).deleteById(translationId));
        }
    }

    @Test
    public void test_givenStudyExists_andIsNotTranslated_whenRouteIsCalled_thenItReturns200_andCorrectSummaries() throws IOException {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(token, buildGetStudiesUrl(umbrella)).execute();
        HttpResponse httpResponse = res.returnResponse();

        assertEquals(HttpStatus.SC_OK, httpResponse.getStatusLine().getStatusCode());

        Type listType = new TypeToken<ArrayList<StudySummary>>() {}.getType();
        ArrayList<StudySummary> studySummaries = gson.fromJson(EntityUtils.toString(httpResponse.getEntity()), listType);

        assertEquals(2, studySummaries.size());

        StudySummary studySummary = studySummaries.get(0);
        assertEquals(studyDtos.get(0).getName(), studySummary.getName());
        assertEquals(studyDtos.get(0).getGuid(), studySummary.getStudyGuid());
        assertEquals(enrollmentStatusCounts.get(0).getParticipantCount(), studySummary.getParticipantCount());
        assertEquals(enrollmentStatusCounts.get(0).getRegisteredCount(), studySummary.getRegisteredCount());
        assertEquals(studyDtos.get(0).getStudyEmail(), studySummary.getStudyEmail());

        studySummary = studySummaries.get(1);
        assertEquals(studyDtos.get(1).getName(), studySummary.getName());
        assertEquals(studyDtos.get(1).getGuid(), studySummary.getStudyGuid());
        assertEquals(enrollmentStatusCounts.get(1).getParticipantCount(), studySummary.getParticipantCount());
        assertEquals(enrollmentStatusCounts.get(1).getRegisteredCount(), studySummary.getRegisteredCount());
        assertEquals(studyDtos.get(1).getStudyEmail(), studySummary.getStudyEmail());
    }

    @Test
    public void testFailsCorrectly_when_umbrellaIsEmpty() throws IOException {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(token, buildGetStudiesUrl("")).execute();
        HttpResponse httpResponse = res.returnResponse();

        assertEquals(HttpStatus.SC_BAD_REQUEST, httpResponse.getStatusLine().getStatusCode());

        HttpEntity entity = httpResponse.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        ApiError error = gson.fromJson(bodyToString, ApiError.class);
        Assert.assertEquals(error.getCode(), ErrorCodes.REQUIRED_PARAMETER_MISSING);
        Assert.assertEquals(error.getMessage(), "Missing umbrella parameter");
    }

    @Test
    public void testFailsCorrectly_when_umbrellaHasNoStudies() throws IOException {
        Response res = RouteTestUtil.buildAuthorizedGetRequest(token, buildGetStudiesUrl("veryFakeUmbrellaYes")).execute();
        HttpResponse httpResponse = res.returnResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, httpResponse.getStatusLine().getStatusCode());

        HttpEntity entity = httpResponse.getEntity();
        String bodyToString = EntityUtils.toString(entity);
        ApiError error = gson.fromJson(bodyToString, ApiError.class);
        Assert.assertEquals(error.getCode(), ErrorCodes.NOT_FOUND);
        Assert.assertEquals(error.getMessage(), "No studies for umbrella");
    }

}
