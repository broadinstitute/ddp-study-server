package org.broadinstitute.ddp.route;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetCancerSuggestionsRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetCancerSuggestionsRouteTest.class);
    private static final String URL_TEMPLATE = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.CANCER_SUGGESTION;
    private static String token;

    @BeforeClass
    public static void setupClass() {
        TestDataSetupUtil.GeneratedTestData testData = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateBasicUserTestData(handle);
        });
        token = testData.getTestingUser().getToken();
    }

    private String createUrlFromTemplate(String urlTemplate, String query, String limit) {
        String url = urlTemplate.replace(RouteConstants.PathParam.STUDY_GUID, TestData.STUDY_GUID);
        return url + "?" + RouteConstants.QueryParam.TYPEAHEAD_QUERY + "=" + query
                + "&" + RouteConstants.QueryParam.TYPEAHEAD_QUERY_LIMIT + "=" + limit;
    }

    private String createUrlFromTemplate(String urlTemplate) {
        return createUrlFromTemplate(urlTemplate, TestData.CANCER_QUERY, TestData.CANCER_QUERY_LIMIT);
    }

    private String createUrlFromTemplate(String urlTemplate, String query) {
        return createUrlFromTemplate(urlTemplate, query, TestData.CANCER_QUERY_LIMIT);
    }

    @Test
    public void givenNoCancerNameMatchesPattern_whenRouteIsCalled_thenItReturnsEmptySuggestionList() {
        CancerStore.getInstance().populate(Collections.emptyList());
        String url = createUrlFromTemplate(URL_TEMPLATE);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(0));
    }

    @Test
    public void givenOneCancerNameMatchesPattern_whenRouteIsCalled_thenItReturnsListWithValidSingleItem() {
        CancerStore.getInstance().populate(Arrays.asList(TestData.CANCER_NAME));
        String url = createUrlFromTemplate(URL_TEMPLATE);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results[0].cancer.name", Matchers.equalTo(TestData.CANCER_NAME))
        .body("results[0].matches", Matchers.hasSize(1))
        .body("results[0].matches[0].offset", Matchers.is(0))
        .body("results[0].matches[0].length", Matchers.is(TestData.CANCER_QUERY.length()));
    }

    @Test
    public void givenPatternMatchesCancerNameMultipleTimes_whenRouteIsCalled_thenItReturnsSingleMatch() {
        CancerStore.getInstance().populate(Arrays.asList("Sarcoma"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "s");
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results[0].matches", Matchers.hasSize(1))
        .body("results[0].matches[0].offset", Matchers.is(0));
    }

    @Test
    public void givenManyCancerNamesMatchPattern_whenRouteIsCalled_thenItReturnsListWithSuggestions() {
        CancerStore.getInstance().populate(Arrays.asList("Sarcoma", "Carcinoma", "Melanoma"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "noma");
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(2))
        .body("results.cancer.name", Matchers.hasItems("Carcinoma", "Melanoma"))
        .body("results[0].matches", Matchers.hasSize(1))
        .body("results[1].matches", Matchers.hasSize(1))
        .body("results[1].matches[0].offset", Matchers.is(5));
    }

    @Test
    public void givenQueryIsNotSpecified_whenRouteIsCalled_thenReturnedSuggestionsContainAllCancers() {
        CancerStore.getInstance().populate(Arrays.asList("Sarcoma", "Carcinoma", "Melanoma"));
        String query = "";
        String url = createUrlFromTemplate(URL_TEMPLATE, query);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("query", Matchers.is(query))
        .body("results", Matchers.hasSize(3))
        .body("results.cancer.name", Matchers.hasItems("Sarcoma", "Carcinoma", "Melanoma"));
    }

    @Test
    public void givenQueryIsMalformed_whenRouteIsCalled_thenItReturns400() {
        CancerStore.getInstance().populate(Arrays.asList("Sarcoma", "Carcinoma", "Melanoma"));
        String query = "Sarcoma!";
        String url = createUrlFromTemplate(URL_TEMPLATE, query);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    public void givenPatternContainsMetachars_whenRouteIsCalled_thenPatternWorksAsLiteralText() {
        CancerStore.getInstance().populate(Arrays.asList("Sarcoma (Angiosarcoma cancer)"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "Sarcoma (Angiosarcoma");
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results.cancer.name", Matchers.hasItems("Sarcoma (Angiosarcoma cancer)"));
    }

    @Test
    public void givenCancerNameAndPatternHaveDifferentCase_whenRouteIsCalled_thenPatternMatchesCancerName() {
        CancerStore.getInstance().populate(Arrays.asList("SARCOMA"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "sarcoma");
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results.cancer.name", Matchers.hasItems("SARCOMA"));
    }

    @Test
    public void testResultSortOrder() {
        String first = "FOO BAR";
        String third = "MCFOO";
        String second = "BAR FOO";
        String last = "BAR 2FOO";
        CancerStore.getInstance().populate(Arrays.asList(second, last, first, third, "no match"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "foo");
        RestAssured.given().auth().oauth2(token)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(4))
                .body("results[0].matches[0].offset", Matchers.is(0))
                .body("results[1].matches[0].offset", Matchers.is(4))
                .body("results[2].matches[0].offset", Matchers.is(2))
                .body("results[3].matches[0].offset", Matchers.is(5));
    }

    @Test
    public void testSanitization() {
        CancerStore.getInstance().populate(Arrays.asList("foo bar"));

        String url = createUrlFromTemplate(URL_TEMPLATE, "[foo");
        RestAssured.given().auth().oauth2(token)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(0));

        url = createUrlFromTemplate(URL_TEMPLATE, "foo.*");
        RestAssured.given().auth().oauth2(token)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(0));
    }

    @Test
    public void givenLookupWithCyrillicCharacterThatDoesNotMatchAnything() {
        CancerStore.getInstance().populate(List.of("SARCOMA", "MELANOMA"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "ффф");
        RestAssured.given().auth().oauth2(token)

                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(0))
                .and().extract().response().prettyPrint();
    }

    private static class TestData {
        public static final String STUDY_GUID = "test-study1";
        public static final String CANCER_QUERY = "Men";
        public static final String CANCER_QUERY_LIMIT = String.valueOf(10);
        public static final String CANCER_NAME = "Meningioma";
    }
}
