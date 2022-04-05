package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.service.UserDeleteService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DeleteUserRouteTest extends DeleteUserRouteTestAbstract {

    private static UserDeleteService userDeleteService;
    private static DeleteUserRoute route;

    @BeforeClass
    public static void setup() throws Exception {
        userDeleteService = new UserDeleteService(esClientMock);
        route = new DeleteUserRoute(userDeleteService);
        DeleteUserRouteTestAbstract.setup();
    }

    @AfterClass
    public static void cleanup() {
        DeleteUserRouteTestAbstract.cleanup();
    }

    @Test
    public void nonGoverned() {
        TransactionWrapper.useTxn(handle -> {
            DeleteUserRoute.CheckError err = route.checkLimits(handle, userNonGoverned, testData.getUserGuid());
            assertNotNull(err);
            assertEquals(401, err.getStatus());
        });
    }

    @Test
    public void multiGoverned() {
        TransactionWrapper.useTxn(handle -> {
            DeleteUserRoute.CheckError err = route.checkLimits(handle, userMultiGoverned, testData.getUserGuid());
            assertNotNull(err);
            assertEquals(422, err.getStatus());
        });
    }

    @Test
    public void withAccount() {
        TransactionWrapper.useTxn(handle -> {
            DeleteUserRoute.CheckError err = route.checkLimits(handle, userWithAccount, testData.getUserGuid());
            assertNotNull(err);
            assertEquals(422, err.getStatus());
        });
    }

    @Test
    public void enrolled() {
        TransactionWrapper.useTxn(handle -> {
            DeleteUserRoute.CheckError err = route.checkLimits(handle, userEnrolled, testData.getUserGuid());
            assertNotNull(err);
            assertEquals(422, err.getStatus());
        });
    }

    @Test
    public void withKit() {
        TransactionWrapper.useTxn(handle -> {
            DeleteUserRoute.CheckError err = route.checkLimits(handle, userWithKit, testData.getUserGuid());
            assertNotNull(err);
            assertEquals(422, err.getStatus());
        });
    }

    @Test
    public void normal() throws IOException {
        TransactionWrapper.useTxn(handle -> {
            DeleteUserRoute.CheckError err = route.checkLimits(handle, userNormal, testData.getUserGuid());
            assertNull(err);
            userDeleteService.simpleDelete(handle, userNormal, "TestAdmin", "test deletion");
            assertFalse(handle.attach(UserDao.class).findUserById(userNormal.getId()).isPresent());
        });
        usersToDelete.remove(userNormal);
        governancesToDelete.remove(userNormal);
    }
}
