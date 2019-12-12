package org.broadinstitute.ddp.util;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class Auth0UtilTest extends TxnAwareBaseTest {

    private static Auth0Util auth0Util;
    private static Auth0MgmtTokenHelper auth0MgmtTokenHelper;
    private static TestDataSetupUtil.GeneratedTestData basicTestData;

    @BeforeClass
    public static void setUp() {
        TransactionWrapper.useTxn(handle -> {
            basicTestData = TestDataSetupUtil.generateBasicUserTestData(handle);
            auth0MgmtTokenHelper = Auth0Util.getManagementTokenHelperForStudy(handle, basicTestData.getStudyGuid());
            auth0Util = new Auth0Util(auth0MgmtTokenHelper.getDomain());
        });
    }

    @Test
    public void testCreateResetPasswordLink() throws MalformedURLException, Auth0Exception {
        String connectionId = auth0Util.getAuth0UserNamePasswordConnectionId(auth0MgmtTokenHelper.getManagementApiToken());
        assertNotNull(connectionId);
        String resetLink = auth0Util.generatePasswordResetLink(basicTestData.getTestingUser().getEmail(), connectionId,
                auth0MgmtTokenHelper.getManagementApiToken(), "https://www.wikipedia.org");
        assertNotNull(resetLink);
        new URL(resetLink);
    }

    @Ignore
    public void testListUsersByEmail() throws MalformedURLException, Auth0Exception {
        List<User> userList = auth0Util.getAuth0UsersByEmail(
                basicTestData.getTestingUser().getEmail(),
                auth0MgmtTokenHelper.getManagementApiToken(),
                Auth0Util.USERNAME_PASSWORD_AUTH0_CONN_NAME);
        assertNotNull(userList);
        assertEquals(1, userList.size());
        User user = userList.get(0);
        assertEquals(basicTestData.getTestingUser().getEmail(), user.getEmail());
        assertEquals(Auth0Util.USERNAME_PASSWORD_AUTH0_CONN_NAME, user.getIdentities().get(0).getConnection());

        //pass invalid connection
        userList = auth0Util.getAuth0UsersByEmail(basicTestData.getTestingUser().getEmail(),
                auth0MgmtTokenHelper.getManagementApiToken(),
                "INVALID-CONNECTION");
        assertNotNull(userList);
        assertEquals(0, userList.size());

    }

}
