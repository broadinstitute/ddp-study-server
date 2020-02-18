package org.broadinstitute.ddp.filter;

import java.util.Locale;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.route.IntegrationTestSuite;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class StudyLanguageContentLanguageSettingFilterTest extends IntegrationTestSuite.TestCase {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String url;
    private static ActivityInstanceDto instanceDto;
    private static final String CONTENT_LANGUAGE_FORMAT_REGEX = "[a-z]{2}";

    @BeforeClass
    public static void setupClass() {
        TransactionWrapper.useTxn(
                handle -> {
                    testData = TestDataSetupUtil.generateBasicUserTestData(handle);
                    FormActivityDef activityDef = TestDataSetupUtil.generateTestFormActivityForUser(
                            handle, testData.getUserGuid(), testData.getStudyGuid()
                    );
                    instanceDto = TestDataSetupUtil.generateTestFormActivityInstanceForUser(
                            handle, activityDef.getActivityId(), testData.getUserGuid()
                    );
                }
        );
        String endpoint = RouteConstants.API.USER_ACTIVITIES_INSTANCE
                .replace(RouteConstants.PathParam.USER_GUID, testData.getUserGuid())
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid())
                .replace(RouteConstants.PathParam.INSTANCE_GUID, "{instanceGuid}");
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @Test
    public void test_whenCreateHeaderFromLocaleIsCalled_thenItReturnsCorrectHeader() {
        Locale locale = Locale.forLanguageTag("en-GB");
        String header = StudyLanguageContentLanguageSettingFilter.createHeaderFromLocale(locale);
        Assert.assertEquals("en", locale.getLanguage());
        Assert.assertEquals("GB", locale.getCountry());
        Assert.assertEquals("en-GB", header);
    }

    @Test
    // Verifies that the route in the study context has a valid "Content-Language" header
    // This effectively means that both StudyLanguageContentLanguageSettingFilter and
    // StudyLanguageResolutionFilter worked as expected
    public void test_whenLanguageAwareRouteIsCalled_andResponseStatusIs200_thenResponseContainsValidContentLanguageHeader() {
        Response resp = RestAssured.given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url);
        resp.then().assertThat().statusCode(200);
        String header = resp.getHeader("Content-Language");
        Assert.assertNotNull(header);
        Assert.assertEquals("en", header);
    }

    @Test
    public void test_whenLanguageAwareRouteIsCalled_andResponseStatusIsNot200_thenResponseContainsNoContentLanguageHeader() {
        String badToken = "a" + testData.getTestingUser().getToken();
        Response resp = RestAssured.given().auth().oauth2(badToken)
                .pathParam("instanceGuid", instanceDto.getGuid())
                .when().get(url);
        resp.then().assertThat().statusCode(401);
        String header = resp.getHeader("Content-Language");
        Assert.assertNull(header);
    }

    @Test
    public void test_whenNonLanguageAwareRouteIsCalled_andResponseStatusIs200_thenResponseContainsNoContentLanguageHeader() {
        String url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.HEALTH_CHECK;
        String password = RouteTestUtil.getConfig().getString(ConfigFile.HEALTHCHECK_PASSWORD);
        Response resp = RestAssured.given().header("Host", password)
                .when().get(url);
        resp.then().assertThat().statusCode(200);
        String header = resp.getHeader("Content-Language");
        Assert.assertNull(header);
    }

}
