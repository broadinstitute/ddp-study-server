package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET;

import java.sql.SQLException;
import java.util.Map;

import com.auth0.json.mgmt.users.User;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Auth0LocalRegistrationTest extends TxnAwareBaseTest {

    private static final String fakeClient = "FakeTestClient" + System.currentTimeMillis();
    private static final Logger LOG = LoggerFactory.getLogger(Auth0Util.class);
    private static String testUserAuth0UserId;

    private static String auth0Domain;

    private static Auth0ManagementClient mgmtClient;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String testUserGuid;
    private String fakeUserGuid = Long.toString(System.currentTimeMillis());

    @BeforeClass
    public static void setUp() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        testUserGuid = testData.getUserGuid();
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        String auth0MgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
        String auth0MgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
        auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        mgmtClient = new Auth0ManagementClient(auth0Domain, auth0MgmtClientId, auth0MgmtSecret);
    }

    @After
    public void removeFakeTestClientGuidFromAuth0() {
        if (StringUtils.isNotBlank(testUserAuth0UserId)) {
            User testUser = mgmtClient.removeUserGuidForAuth0User(testUserAuth0UserId, fakeClient);
            Map<String, Object> appMetadata = testUser.getAppMetadata();
            if (appMetadata != null) {
                Map userGuidMap = (Map) appMetadata.get(Auth0ManagementClient.APP_METADATA_PEPPER_USER_GUIDS);
                if (userGuidMap != null) {
                    LOG.info("Deleted fake client " + fakeClient + " from test user's list of guid.  "
                            + testUserGuid + " has " + userGuidMap.size() + " remaining client/guid mappings.");
                }
            }
        }
    }

    private String getAuth0UserIdForTestUser() throws SQLException {
        return ConfigManager.getInstance().getConfig().getConfig(ConfigFile.AUTH0).getString(ConfigFile.TEST_USER_AUTH0_ID);
    }

    /**
     * Verifies that setting the client-specific user guid in auth0's metadata works properly
     * for local registration.
     */
    @Test
    public void testSetDDPUserGuidForAuth0User() throws Exception {
        testUserAuth0UserId = getAuth0UserIdForTestUser();
        User auth0User = mgmtClient.setUserGuidForAuth0User(testUserAuth0UserId, fakeClient, fakeUserGuid);
        String ddpGuidFromAuth0UserAppMetadata = ((Map) auth0User.getAppMetadata()
                .get(Auth0ManagementClient.APP_METADATA_PEPPER_USER_GUIDS)).get(fakeClient).toString();
        Assert.assertEquals(fakeUserGuid, ddpGuidFromAuth0UserAppMetadata);
    }
}
