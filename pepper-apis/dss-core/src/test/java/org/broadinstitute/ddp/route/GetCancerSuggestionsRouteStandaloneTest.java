package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.CancerItem;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class GetCancerSuggestionsRouteStandaloneTest extends IntegrationTestSuite.TestCase {
    private static final String URL_TEMPLATE = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.CANCER_SUGGESTION;
    private static String token;
    private static TestDataSetupUtil.GeneratedTestData testData;


    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
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
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(List.of(TestData.CANCER_NAME), "en"));
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
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(List.of("Sarcoma"), "en"));
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
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(
                List.of("Sarcoma", "Carcinoma", "Melanoma"), "en"));
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
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(
                List.of("Sarcoma", "Carcinoma", "Melanoma"), "en"));
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
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(
                List.of("Sarcoma", "Carcinoma", "Melanoma"), "en"));
        String query = "Sarcoma!";
        String url = createUrlFromTemplate(URL_TEMPLATE, query);
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON);
    }

    @Test
    public void givenPatternContainsMetachars_whenRouteIsCalled_thenPatternWorksAsLiteralText() {
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(List.of("Sarcoma (Angiosarcoma cancer)"),
                "en"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "Sarcoma (Angiosarcoma");
        RestAssured.given().auth().oauth2(token)
        .when().get(url).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("results", Matchers.hasSize(1))
        .body("results.cancer.name", Matchers.hasItems("Sarcoma (Angiosarcoma cancer)"));
    }

    @Test
    public void givenCancerNameAndPatternHaveDifferentCase_whenRouteIsCalled_thenPatternMatchesCancerName() {
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(List.of("SARCOMA"), "en"));
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
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(
                List.of(second, last, first, third, "no match"), "en"));
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
    public void testSpanishChars() {
        UserProfile originalProfile = testData.getProfile();
        String spanishCancer = "é123 and ñ456";
        String englishCancer = "This is English";

        List<CancerItem> cancersWithTwoLanguages = new ArrayList<>();
        cancersWithTwoLanguages.add(new CancerItem(englishCancer, "en"));
        cancersWithTwoLanguages.add(new CancerItem(spanishCancer, "es"));

        CancerStore.getInstance().populate(cancersWithTwoLanguages);

        String url = createUrlFromTemplate(URL_TEMPLATE, "is");
        RestAssured.given().auth().oauth2(token)
                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(1))
                .body("results[0].cancer.name", Matchers.is(englishCancer));

        // change profile to Spanish
        try {
            TransactionWrapper.useTxn(handle -> {
                var userProfileBuilder = new UserProfile(originalProfile).toBuilder();
                userProfileBuilder.preferredLangCode("es");
                var profileDao = handle.attach(UserProfileDao.class);
                profileDao.updateProfile(userProfileBuilder.build());
            });

            url = createUrlFromTemplate(URL_TEMPLATE, "ñ");
            RestAssured.given().auth().oauth2(token)
                    .when().get(url).then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("results", Matchers.hasSize(1))
                    .body("results[0].cancer.name", Matchers.is(spanishCancer));
        } finally {
            // reset language to English
            TransactionWrapper.useTxn(handle -> {
                var userProfileBuilder = new UserProfile(originalProfile).toBuilder();
                var profileDao = handle.attach(UserProfileDao.class);
                profileDao.updateProfile(userProfileBuilder.build());
            });
        }
    }

    @Test
    public void testSanitization() {
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(List.of("foo bar"), "en"));

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
        CancerStore.getInstance().populate(CancerItem.toCancerItemList(List.of("SARCOMA", "MELANOMA"), "en"));
        String url = createUrlFromTemplate(URL_TEMPLATE, "ффф");
        RestAssured.given().auth().oauth2(token)

                .when().get(url).then().assertThat()
                .statusCode(200).contentType(ContentType.JSON)
                .body("results", Matchers.hasSize(0))
                .and().extract().response().prettyPrint();
    }

    @AfterClass
    public static void checkProfileLanguage() {
        // make sure that the profile language was reset to what it was at the start of the test
        TransactionWrapper.useTxn(handle -> {
            var profileDao = handle.attach(UserProfileDao.class);
            var userProfile = profileDao.findProfileByUserGuid(testData.getUserGuid()).get();
            Assert.assertEquals(testData.getProfile().getPreferredLangCode(), userProfile.getPreferredLangCode());
        });
    }

    private static class TestData {
        public static final String STUDY_GUID = "test-study1";
        public static final String CANCER_QUERY = "Men";
        public static final String CANCER_QUERY_LIMIT = String.valueOf(10);
        public static final String CANCER_NAME = "Meningioma";
    }
}
