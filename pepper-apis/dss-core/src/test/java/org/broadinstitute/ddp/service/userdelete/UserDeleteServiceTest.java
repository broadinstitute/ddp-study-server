package org.broadinstitute.ddp.service.userdelete;

import static java.lang.String.format;
import static org.broadinstitute.ddp.service.UserDeleteService.getUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.User;
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

    private static final String WHO_DELETED_USER = "operatorGuid=I2OMJ257OGNF0GY30HQT";
    private static final String DELETION_COMMENT = "This user is deleted by mistake";

    private static UserDeleteService userDeleteService;
    private static User userWithGovernedUsers;


    @BeforeClass
    public static void setup() throws Exception {
        userDeleteService = new UserDeleteService(esClientMock);
        DeleteUserRouteTestAbstract.setup();
        userWithGovernedUsers = new User(testData.getUserId(), testData.getUserGuid(), "testUser-hruid",
                "testUser-altpid", "testUser-shortid",
                false, 1L, 1L, "auth", 0, 0, null, null);
    }

    @AfterClass
    public static void cleanup() {
        DeleteUserRouteTestAbstract.cleanup();
    }

    @Test
    public void testFullDeleteUserWithGovernedUsers() {
        try {
            TransactionWrapper.useTxn(handle -> userDeleteService
                    .fullDelete(handle, userWithGovernedUsers, WHO_DELETED_USER, DELETION_COMMENT));
            fail();
        } catch (DDPException e) {
            assertEquals(format("User [guid=%s] deletion is FAILED: the user has governed users",
                    userWithGovernedUsers.getGuid()), e.getMessage());
        }
    }

    @Test
    public void testFullDeleteNonExistingUser() {
        try {
            TransactionWrapper.useTxn(handle -> userDeleteService
                    .fullDelete(handle, getUser(handle, NON_EXISTING_USER_GUID), WHO_DELETED_USER, DELETION_COMMENT));
        } catch (Exception e) {
            assertEquals("User [guid=aaabbbccc] deletion is FAILED: the user not found", e.getMessage());
        }
    }

    @Test
    public void testFullDeleteUserNormal() {
        TransactionWrapper.useTxn(handle -> userDeleteService
                .fullDelete(handle, userMultiGoverned, WHO_DELETED_USER, DELETION_COMMENT));
        usersToDelete.remove(userMultiGoverned);
        governancesToDelete.remove(userMultiGoverned);
    }

    @Test
    public void testFullDeleteUserEnrolled() {
        TransactionWrapper.useTxn(handle -> userDeleteService
                .fullDelete(handle, userEnrolled, WHO_DELETED_USER, DELETION_COMMENT));
        usersToDelete.remove(userEnrolled);
        governancesToDelete.remove(userEnrolled);
    }

    @Test
    public void testFullDeleteUserNoGovernance() {
        TransactionWrapper.useTxn(handle -> userDeleteService
                .fullDelete(handle, userNoGovernance, WHO_DELETED_USER, DELETION_COMMENT));
        usersToDelete.remove(userNoGovernance);
        governancesToDelete.remove(userNoGovernance);
    }
}
