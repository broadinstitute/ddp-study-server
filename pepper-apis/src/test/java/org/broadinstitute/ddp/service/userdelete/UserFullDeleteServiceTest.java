package org.broadinstitute.ddp.service.userdelete;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.route.DeleteUserRouteTestAbstract;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test {@link UserFullDeleteService}
 */
public class UserFullDeleteServiceTest extends DeleteUserRouteTestAbstract {

    private static final String NON_EXISTING_USER_GUID = "aaabbbccc";

    private static UserFullDeleteService userFullDeleteService;

    @BeforeClass
    public static void setup() throws Exception {
        userFullDeleteService = new UserFullDeleteService(new UserService(esClientMock));
        DeleteUserRouteTestAbstract.setup();
    }

    @AfterClass
    public static void cleanup() {
        DeleteUserRouteTestAbstract.cleanup();
    }

    @Test
    public void testFullDeleteUserWithGovernedUsers() throws IOException {
        try {
            userFullDeleteService.deleteUser(testData.getUserGuid());
        } catch (DDPException e) {
            assertEquals(format("User [guid=19i3-test-user-48f0] full deletion is FAILED: the user has governed users",
                    testData.getUserGuid()), e.getMessage());
        }
    }

    @Test
    public void testFullDeleteUserWithAuthInvalidAccount() {
        try {
            userFullDeleteService.deleteUser(userWithAccount.getGuid());
        } catch (Exception e) {
            assertEquals("com.auth0.exception.APIException: Request failed with status code 400: "
                    + "Object didn't pass validation for format user-id: some_account", e.getMessage());
        }
    }

    @Test
    public void testFullDeleteNonExistingUser() throws IOException {
        try {
            userFullDeleteService.deleteUser(NON_EXISTING_USER_GUID);
        } catch (Exception e) {
            assertEquals("User [guid=aaabbbccc] full deletion is FAILED: the user not found", e.getMessage());
        }
    }

    @Test
    public void testFullDeleteUserNormal() throws IOException {
        userFullDeleteService.deleteUser(userMultiGoverned.getGuid());
        usersToDelete.remove(userMultiGoverned);
        governancesToDelete.remove(userMultiGoverned);
    }
}
