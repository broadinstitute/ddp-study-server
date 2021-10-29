package org.broadinstitute.ddp.service.userdelete;

import static java.lang.String.format;
import static org.broadinstitute.ddp.service.UserDeleteService.getUser;
import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.route.DeleteUserRouteTestAbstract;
import org.broadinstitute.ddp.service.UserDeleteService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test {@link UserDeleteService}
 */
public class UserDeleteServiceTest extends DeleteUserRouteTestAbstract {

    private static final String NON_EXISTING_USER_GUID = "aaabbbccc";

    private static UserDeleteService userDeleteService;

    @BeforeClass
    public static void setup() throws Exception {
        userDeleteService = new UserDeleteService(esClientMock);
        DeleteUserRouteTestAbstract.setup();
    }

    @AfterClass
    public static void cleanup() {
        DeleteUserRouteTestAbstract.cleanup();
    }

    @Test
    public void testFullDeleteUserWithGovernedUsers() throws IOException {
        try {
            TransactionWrapper.useTxn(handle -> {
                userDeleteService.fullDelete(handle, userNonGoverned);
            });
        } catch (DDPException e) {
            assertEquals(format("User [guid=19i3-test-user-48f0] deletion is FAILED: the user has governed users",
                    testData.getUserGuid()), e.getMessage());
        }
    }

    @Test
    public void testFullDeleteNonExistingUser() throws IOException {
        try {
            TransactionWrapper.useTxn(handle -> {
                userDeleteService.fullDelete(handle, getUser(handle, NON_EXISTING_USER_GUID));
            });
        } catch (Exception e) {
            assertEquals("User [guid=aaabbbccc] deletion is FAILED: the user not found", e.getMessage());
        }
    }

    @Test
    public void testFullDeleteUserNormal() throws IOException {
        TransactionWrapper.useTxn(handle -> {
            userDeleteService.fullDelete(handle, userMultiGoverned);
        });
        usersToDelete.remove(userMultiGoverned);
        governancesToDelete.remove(userMultiGoverned);
    }
}
