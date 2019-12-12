package org.broadinstitute.ddp.util;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.exception.APIException;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.auth0.Auth0CallResponse;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.mockito.Mockito;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests methods updating the user login data in Auth0
 * Big enough to reside in its own file (could be in Auth0UtilTest as well)
 */
public class UpdateUserLoginDataUtilTest extends TxnAwareBaseTest {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateUserLoginDataUtilTest.class);
    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setupClass() {
        testData = TransactionWrapper.withTxn(handle -> TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    public UserDto convertTestingUserToDto(Auth0Util.TestingUser testingUser) {
        return new UserDto(
            testingUser.getUserId(),
            testingUser.getAuth0Id(),
            testingUser.getUserGuid(),
            null,
            null,
            null,
            0,
            0
        );
    }

    private Auth0CallResponse updateUserEmail(ManagementAPI mgmtAPI, Auth0Util.TestingUser testingUser, String newEmail) {
        return Auth0Util.updateUserEmail(
                mgmtAPI,
                convertTestingUserToDto(testingUser),
                newEmail
        );
    }

    private Auth0CallResponse updateUserPassword(ManagementAPI mgmtAPI, Auth0Util.TestingUser testingUser, String newPassword) {
        return Auth0Util.updateUserPassword(
                mgmtAPI,
                convertTestingUserToDto(testingUser),
                newPassword
        );
    }

    @Test
    public void test_givenTokenUserIdAndEmailAreOk_whenUpdateUserEmailIsCalled_thenItReturnsSuccessCode() throws Auth0Exception {
        User requestPayload = new User();
        requestPayload.setEmail(TestData.NEW_EMAIL);
        ManagementAPI mgmtAPI = Mockito.mock(ManagementAPI.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mgmtAPI.users().update(Mockito.any(), Mockito.any()).execute()).thenReturn(requestPayload);

        Auth0CallResponse response = updateUserEmail(
                mgmtAPI,
                testData.getTestingUser(),
                TestData.NEW_EMAIL
        );

        Assert.assertEquals(Auth0CallResponse.Auth0Status.SUCCESS, response.getAuth0Status());
    }

    @Test
    public void test_givenTokenUserIdAndPasswordAreOk_whenUpdateUserPasswdCalled_thenItSuccessCode() throws Auth0Exception {
        ManagementAPI mgmtAPI = Mockito.mock(ManagementAPI.class, Mockito.RETURNS_DEEP_STUBS);
        User requestPayload = new User();
        requestPayload.setPassword(TestData.NEW_PASSWORD);
        User responsePayload = new User();
        Mockito.when(mgmtAPI.users().update(Mockito.any(), Mockito.any()).execute()).thenReturn(responsePayload);

        Auth0CallResponse response = updateUserPassword(
                mgmtAPI,
                testData.getTestingUser(),
                TestData.NEW_PASSWORD
        );

        Assert.assertEquals(Auth0CallResponse.Auth0Status.SUCCESS, response.getAuth0Status());
    }

    @Test
    public void test_whenUpdatingRoutineIsCalledAndCallFailsDueToApiError_thenItReturnsUnknownProblem() throws Auth0Exception {
        ManagementAPI mgmtAPI = Mockito.mock(ManagementAPI.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mgmtAPI.users().update(Mockito.any(), Mockito.any()).execute()).thenThrow(
                new APIException("The email is invalid", 400, null)
        );

        Auth0CallResponse response = updateUserEmail(
                mgmtAPI,
                testData.getTestingUser(),
                TestData.NEW_EMAIL
        );

        Assert.assertEquals(Auth0CallResponse.Auth0Status.UNKNOWN_PROBLEM, response.getAuth0Status());
    }

    @Test
    public void test_whenUpdatingRoutineIsCalledAndCallMysteriouslyFails_thenItReturnsUnknownProblem() throws Auth0Exception {
        ManagementAPI mgmtAPI = Mockito.mock(ManagementAPI.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mgmtAPI.users().update(Mockito.any(), Mockito.any()).execute()).thenThrow(
                new Auth0Exception("Something bad happened")
        );

        Auth0CallResponse response = updateUserEmail(
                mgmtAPI,
                testData.getTestingUser(),
                TestData.NEW_EMAIL
        );

        Assert.assertEquals(Auth0CallResponse.Auth0Status.UNKNOWN_PROBLEM, response.getAuth0Status());
    }

    @Test
    public void test_givenPasswordIsTooWeak_whenUpdatePasswdIsCalled_thenItReturnsPasswordTooWeak() throws Auth0Exception {
        ManagementAPI mgmtAPI = Mockito.mock(ManagementAPI.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mgmtAPI.users().update(Mockito.any(), Mockito.any()).execute()).thenThrow(
                new APIException("PasswordStrengthError: Password is too weak", 400, null)
        );

        Auth0CallResponse response = updateUserPassword(
                mgmtAPI,
                testData.getTestingUser(),
                TestData.NEW_PASSWORD
        );
        Assert.assertEquals(Auth0CallResponse.Auth0Status.PASSWORD_TOO_WEAK, response.getAuth0Status());
    }

    @Test
    public void test_givenEmailExistsInAuth_whenUpdateEmailIsCalled_thenItReturnsEmailAlreadyExists() throws Auth0Exception {
        ManagementAPI mgmtAPI = Mockito.mock(ManagementAPI.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mgmtAPI.users().update(Mockito.any(), Mockito.any()).execute()).thenThrow(
                new APIException("The specified new email already exists", 400, null)
        );

        Auth0CallResponse response = updateUserEmail(
                mgmtAPI,
                testData.getTestingUser(),
                TestData.NEW_EMAIL
        );
        Assert.assertEquals(Auth0CallResponse.Auth0Status.EMAIL_ALREADY_EXISTS, response.getAuth0Status());
    }

    @Test
    public void test_givenEmailIsMalformed_whenUpdatingRoutineIsCalled_thenItReturnsMalformedEmail() throws Auth0Exception {
        ManagementAPI mgmtAPI = Mockito.mock(ManagementAPI.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(mgmtAPI.users().update(Mockito.any(), Mockito.any()).execute()).thenThrow(
                new APIException(
                        "Payload validation error: 'Object didn't pass validation for format email",
                        400,
                        null
                )
        );

        Auth0CallResponse response = updateUserPassword(
                mgmtAPI,
                testData.getTestingUser(),
                TestData.NEW_EMAIL
        );
        Assert.assertEquals(Auth0CallResponse.Auth0Status.MALFORMED_EMAIL, response.getAuth0Status());
    }

    private static class TestData {
        public static final String NEW_EMAIL = "aaa@bbb.com";
        public static final String NEW_PASSWORD = "fgghsdfghka";
        public static final String AUTH0_TOKEN = "aabbcc";
        public static final String NONEXISTENT_USER_GUID = "dfhadsffasdfh";
        public static final Auth0CallResponse SUCCESS_RESPONSE = new Auth0CallResponse(
                200,
                null
        );
    }
}
