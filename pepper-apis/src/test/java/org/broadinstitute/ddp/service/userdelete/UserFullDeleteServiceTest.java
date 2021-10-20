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
            userFullDeleteService.deleteUser(testData.getStudyGuid(), testData.getUserGuid());
        } catch (DDPException e) {
            assertEquals(format("The user with GUID=%s has governed users and cannot be deleted", testData.getUserGuid()),
                    e.getMessage());
        }
    }

    @Test
    public void testFullDeleteUserWithAuthInvalidAccount() {
        try {
            userFullDeleteService.deleteUser(testData.getStudyGuid(), userWithAccount.getGuid());
        } catch (Exception e) {
            assertEquals("com.auth0.exception.APIException: Request failed with status code 400: "
                    + "Object didn't pass validation for format user-id: some_account", e.getMessage());
        }
    }

    @Test
    public void testFullDeleteUserNormal() throws IOException {
        userFullDeleteService.deleteUser(testData.getStudyGuid(), userMultiGoverned.getGuid());
        usersToDelete.remove(userMultiGoverned);
        governancesToDelete.remove(userMultiGoverned);
    }
}
