package org.broadinstitute.ddp.route;

import static org.hamcrest.Matchers.equalTo;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiStudyPasswordRequirements;
import org.broadinstitute.ddp.util.TestDataSetupUtil;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetStudyPasswordRequirementsRouteTest extends IntegrationTestSuite.TestCase {
    private static final Logger LOG = LoggerFactory.getLogger(GetStudyPasswordRequirementsRouteTest.class);
    private static final String urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.STUDY_PASSWORD_REQUIREMENTS;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;

    private static String makeUrl(String studyGuid) {
        return urlTemplate.replace(RouteConstants.PathParam.STUDY_GUID, studyGuid);
    }

    private static void insertPasswordRequirements() {
        TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiStudyPasswordRequirements.class).insert(
                    testData.getTestingStudy().getAuth0TenantId(),
                    TestData.MIN_LENGTH,
                    TestData.IS_UPPERCASE_LETTER_REQUIRED,
                    TestData.IS_LOWERCASE_LETTER_REQUIRED,
                    TestData.IS_SPECIAL_CHARACTER_REQUIRED,
                    TestData.IS_NUMBER_REQUIRED,
                    TestData.MAX_IDENTICAL_CONSECUTIVE_CHARACTERS
                )
        );
    }

    private static void deletePasswordRequirements() {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiStudyPasswordRequirements.class).deleteById(testData.getTestingStudy().getAuth0TenantId())
        );
    }

    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(handle -> {
            return TestDataSetupUtil.generateBasicUserTestData(handle);
        });
        token = testData.getTestingUser().getToken();
        insertPasswordRequirements();
    }

    @AfterClass
    public static void teardownClass() {
        deletePasswordRequirements();
    }

    @Test
    public void test_givenRequirementsExist_whenRouteIsCalled_thenItReturnsCorrectRequirements() {
        RestAssured.given().auth().oauth2(token)
        .when().get(makeUrl(testData.getStudyGuid())).then().assertThat()
        .statusCode(200).contentType(ContentType.JSON)
        .body("minLength", equalTo(TestData.MIN_LENGTH))
        .body("isUppercaseLetterRequired", equalTo(TestData.IS_UPPERCASE_LETTER_REQUIRED))
        .body("isLowercaseLetterRequired", equalTo(TestData.IS_LOWERCASE_LETTER_REQUIRED))
        .body("isSpecialCharacterRequired", equalTo(TestData.IS_SPECIAL_CHARACTER_REQUIRED))
        .body("isNumberRequired", equalTo(TestData.IS_NUMBER_REQUIRED))
        .body("maxIdenticalConsecutiveCharacters", equalTo(TestData.MAX_IDENTICAL_CONSECUTIVE_CHARACTERS));
    }

    @Test
    public void test_givenRequirementsDontExist_whenRouteIsCalled_thenItReturnsNotFoundAndValidErrorCode() {
        deletePasswordRequirements();
        RestAssured.given().auth().oauth2(token)
        .when().get(makeUrl(testData.getStudyGuid())).then().assertThat()
        .statusCode(404).contentType(ContentType.JSON)
        .body("code", equalTo(ErrorCodes.STUDY_PASSWORD_REQUIREMENTS_NOT_FOUND));
        insertPasswordRequirements();
    }

    @Test
    public void test_givenStudyDoesntExist_whenRouteIsCalledWithItsGuid_thenItReturnsNotFoundAndValidErrorCode() {
        RestAssured.given().auth().oauth2(token)
        .when().get(makeUrl(TestData.NON_EXISTENT_STUDY_GUID)).then().assertThat()
        .statusCode(404).contentType(ContentType.JSON)
        .body("code", equalTo(ErrorCodes.STUDY_NOT_FOUND));
    }

    @Test
    public void test_givenStudyGuidIsBlank_whenRouteIsCalled_thenItReturnsBadRequest() {
        RestAssured.given().auth().oauth2(token)
        .when().get(makeUrl(" ")).then().assertThat()
        .statusCode(400).contentType(ContentType.JSON)
        .body("code", equalTo(ErrorCodes.MISSING_STUDY_GUID));
    }

    private static class TestData {
        public static final int MIN_LENGTH = 8;
        public static final boolean IS_UPPERCASE_LETTER_REQUIRED = true;
        public static final boolean IS_LOWERCASE_LETTER_REQUIRED = true;
        public static final boolean IS_SPECIAL_CHARACTER_REQUIRED = false;
        public static final boolean IS_NUMBER_REQUIRED = true;
        public static final int MAX_IDENTICAL_CONSECUTIVE_CHARACTERS = 2;

        public static final String NON_EXISTENT_STUDY_GUID = "NON_EXISTENT_STUDY_GUID";
    }
}
