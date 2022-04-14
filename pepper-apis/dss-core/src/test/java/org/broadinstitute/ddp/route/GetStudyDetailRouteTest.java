package org.broadinstitute.ddp.route;

import io.restassured.RestAssured;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyI18n;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;

public class GetStudyDetailRouteTest extends IntegrationTestSuite.TestCase {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;
    private static String url;

    @BeforeClass
    public static void setup() throws Exception {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            token = testData.getTestingUser().getToken();
        });
        String endpoint = RouteConstants.API.STUDY_DETAIL
                .replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
        url = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @Test
    public void test_givenStudyExists_andIsNotTranslated_whenRouteIsCalled_thenItReturns200_andFallbackDetails() {
        RestAssured.given().auth().oauth2(token)
                .when().get(url)
                .then().assertThat().statusCode(200)
                .body("name", Matchers.is(Matchers.notNullValue()))
                .body("summary", Matchers.is(Matchers.nullValue()));
    }

    @Test
    public void test_givenPopupSettingUnspecified_whenRouteIsCalled_thenItReturns200_andFalse() {
        RestAssured.given().auth().oauth2(token)
                .when().get(url)
                .then().assertThat().statusCode(200)
                .body("shouldDisplayLanguageChangePopup", Matchers.is(false));
    }

    @Test
    public void test_givenPopupSettingTrue_whenRouteIsCalled_thenItReturns200_andTrue() {
        TransactionWrapper.useTxn(handle -> {
            StudyDao dao = handle.attach(StudyDao.class);
            dao.addSettings(testData.getStudyId(), null, null, false, null, false, true);
        });

        try {
            RestAssured.given().auth().oauth2(token)
              .when().get(url)
              .then().assertThat().statusCode(200)
              .body("shouldDisplayLanguageChangePopup", Matchers.is(true));
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(StudyDao.class).getStudySql().deleteSettings(testData.getStudyId()));
        }
    }

    @Test
    public void test_givenPopupSettingFalse_whenRouteIsCalled_thenItReturns200_andFalse() {
        TransactionWrapper.useTxn(handle -> {
            StudyDao dao = handle.attach(StudyDao.class);
            dao.addSettings(testData.getStudyId(), null, null, false, null, false, false);
        });

        try {
            RestAssured.given().auth().oauth2(token)
              .when().get(url)
              .then().assertThat().statusCode(200)
              .body("shouldDisplayLanguageChangePopup", Matchers.is(false));
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(StudyDao.class).getStudySql().deleteSettings(testData.getStudyId()));
        }
    }

    @Test
    public void test_givenStudyExists_andIsTranslated_whenRouteIsCalled_thenItReturns200_andTranslatedDetails() {
        long translationId = TransactionWrapper.withTxn(handle -> {
            long languageCodeId = LanguageStore.getDefault().getId();
            return handle.attach(JdbiUmbrellaStudyI18n.class).insert(
                    testData.getStudyId(),
                    languageCodeId,
                    "Test study #1",
                    "Test study #1 - description"
            );
        });
        try {
            RestAssured.given().auth().oauth2(token)
                    .when().get(url)
                    .then().assertThat().statusCode(200)
                    .body("name", Matchers.is(Matchers.notNullValue()))
                    .body("name", Matchers.is("Test study #1"))
                    .body("summary", Matchers.is(Matchers.notNullValue()))
                    .body("summary", Matchers.is("Test study #1 - description"));
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(JdbiUmbrellaStudyI18n.class).deleteById(translationId));
        }
    }
}
