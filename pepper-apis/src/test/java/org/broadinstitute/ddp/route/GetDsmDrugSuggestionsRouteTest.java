package org.broadinstitute.ddp.route;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.dsm.DrugStore;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.hamcrest.Matchers;

import org.junit.BeforeClass;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetDsmDrugSuggestionsRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetDsmDrugSuggestionsRouteTest.class);
    private static final String URL_TEMPLATE = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.DSM_DRUG_SUGGESTION;
    private static String token;

    @BeforeClass
    public static void setupClass() {
        TestDataSetupUtil.GeneratedTestData testData = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateBasicUserTestData(handle);
        });
        token = testData.getTestingUser().getToken();
    }

    private String createUrlFromTemplate(String urlTemplate, String query, String limit) {
        String url = URL_TEMPLATE.replace(RouteConstants.PathParam.STUDY_GUID, TestData.STUDY_GUID);
        return url + "?" + RouteConstants.QueryParam.DRUG_QUERY + "=" + query
                + "&" + RouteConstants.QueryParam.DRUG_QUERY_LIMIT + "=" + limit;
    }

    private String createUrlFromTemplate(String urlTemplate) {
        return createUrlFromTemplate(urlTemplate, TestData.DRUG_QUERY, TestData.DRUG_QUERY_LIMIT);
    }

    private String createUrlFromTemplate(String urlTemplate, String query) {
        return createUrlFromTemplate(urlTemplate, query, TestData.DRUG_QUERY_LIMIT);
    }

    @Test
    public void givenNoDrugNameMatchesPattern_whenRouteIsCalled_thenItReturnsEmptySuggestionList() {
        DrugStore.getInstance().populateDrugList(Collections.emptyList());
        String url = createUrlFromTemplate(URL_TEMPLATE);
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(0));
    }

    @Test
    public void givenOneDrugNameMatchesPattern_whenRouteIsCalled_thenItReturnsListWithValidSingleItem() {
        DrugStore.getInstance().populateDrugList(Arrays.asList(TestData.DRUG_NAME));
        String url = createUrlFromTemplate(URL_TEMPLATE);
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results[0].drug.name", Matchers.equalTo(TestData.DRUG_NAME))
        .body("results[0].matches", Matchers.hasSize(1))
        .body("results[0].matches[0].offset", Matchers.is(0))
        .body("results[0].matches[0].length", Matchers.is(TestData.DRUG_QUERY.length()));
    }

    @Test
    public void givenPatternMatchesDrugNameMultipleTimes_whenRouteIsCalled_thenItReturnsSingleMatch() {
        DrugStore.getInstance().populateDrugList(Arrays.asList("aspartam"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "a");
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results[0].matches", Matchers.hasSize(1))
        .body("results[0].matches[0].offset", Matchers.is(0));
    }

    @Test
    public void givenManyDrugNamesMatchPattern_whenRouteIsCalled_thenItReturnsListWithSuggestions() {
        DrugStore.getInstance().populateDrugList(Arrays.asList("Aspirin", "Brand new Aspirin"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "Aspirin");
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(2))
        .body("results.drug.name", Matchers.hasItems("Aspirin", "Brand new Aspirin"))
        .body("results[0].matches", Matchers.hasSize(1))
        .body("results[1].matches", Matchers.hasSize(1))
        .body("results[1].matches[0].offset", Matchers.is(10));
    }

    @Test
    public void givenQueryIsNotSpecified_whenRouteIsCalled_thenReturnedSuggestionsContainAllDrugs() {
        DrugStore.getInstance().populateDrugList(Arrays.asList("Aspirin", "Brand new Aspirin", "Acetominophen"));
        String query = "";
        String url = createUrlFromTemplate(URL_TEMPLATE, query);
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("query", Matchers.is(query))
        .body("results", Matchers.hasSize(3))
        .body("results.drug.name", Matchers.hasItems("Aspirin", "Brand new Aspirin", "Acetominophen"));
    }

    @Test
    public void givenQueryIsMalformed_whenRouteIsCalled_thenItReturns400() {
        DrugStore.getInstance().populateDrugList(Arrays.asList("Aspirin", "Brand new Aspirin", "Acetominophen"));
        String query = "Aspirin!";
        String url = createUrlFromTemplate(URL_TEMPLATE, query);
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    public void givenPatternContainsMetachars_whenRouteIsCalled_thenPatternWorksAsLiteralText() {
        DrugStore.getInstance().populateDrugList(Arrays.asList("Aspirin (Acetylsalicylic acid)"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "Aspirin (Acetylsalicylic");
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results.drug.name", Matchers.hasItems("Aspirin (Acetylsalicylic acid)"));
    }

    @Test
    public void givenDrugNameAndPatternHaveDifferentCase_whenRouteIsCalled_thenPatternMatchesDrugName() {
        DrugStore.getInstance().populateDrugList(Arrays.asList("ASPIRIN"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "Aspirin");
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results.drug.name", Matchers.hasItems("ASPIRIN"));
    }

    @Test
    public void testResultSortOrder() {
        String first = "FOO BAR";
        String third = "MCFOO";
        String second = "BAR FOO";
        String last = "BAR 2FOO";
        DrugStore.getInstance().populateDrugList(Arrays.asList(second, last, first, third, "no match"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "foo");
        LOG.info("Calling the route, url = " + url);
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
        DrugStore.getInstance().populateDrugList(Arrays.asList("foo bar"));

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
        DrugStore.getInstance().populateDrugList(List.of("ASPIRIN", "SUDAFED"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "ффф");
        LOG.info("Calling the route, url = " + url);
        RestAssured.given().auth().oauth2(token)

                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(0))
                .and().extract().response().prettyPrint();
    }

    private static class TestData {
        public static final String STUDY_GUID = "aa-bb-cc";
        public static final String DRUG_QUERY = "Aceto";
        public static final String DRUG_QUERY_LIMIT = String.valueOf(10);
        public static final String DRUG_NAME = "Acetominophen";
    }
}
