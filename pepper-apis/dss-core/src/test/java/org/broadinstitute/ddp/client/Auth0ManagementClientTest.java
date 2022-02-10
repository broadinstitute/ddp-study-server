package org.broadinstitute.ddp.client;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Map;

import com.auth0.json.mgmt.Connection;
import com.auth0.json.mgmt.users.User;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Auth0ManagementClientTest extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(Auth0ManagementClientTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Auth0ManagementClient client;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            client = Auth0ManagementClient.forStudy(handle, testData.getStudyGuid());
        });
    }

    @Test
    public void testListClientConnections() {
        var mgmtClient = spy(client);
        Connection conn1 = new Connection("foo", "strategy");
        conn1.setEnabledClients(List.of("client"));
        Connection conn2 = new Connection("bar", "strategy");
        conn2.setEnabledClients(List.of());
        doReturn(ApiResult.ok(200, List.of(conn1, conn2))).when(mgmtClient).listConnections();
        doReturn("fake-token").when(mgmtClient).getToken();

        var actual = mgmtClient.listClientConnections("client");
        assertNotNull(actual);
        assertTrue(actual.hasBody());
        assertEquals(1, actual.getBody().size());
        assertEquals("foo", actual.getBody().get(0).getName());
    }

    @Test
    public void testListConnections() {
        var actual = client.listConnections();
        assertNotNull(actual);
        assertEquals(200, actual.getStatusCode());
        assertFalse(actual.getBody().isEmpty());
        assertTrue(actual.getBody().stream()
                .anyMatch(conn -> conn.getName().equals(Auth0ManagementClient.DEFAULT_DB_CONN_NAME)));
    }

    @Test
    public void testCreatePasswordResetTicket() {
        String connectionId = client
                .getConnectionByName(Auth0ManagementClient.DEFAULT_DB_CONN_NAME)
                .getBody().getId();
        assertNotNull(connectionId);

        String email = testData.getTestingUser().getEmail();
        String redirectUrl = "https://code.datadonationplatform.org/";
        var actual = client.createPasswordResetTicket(email, connectionId, redirectUrl);
        assertEquals(200, actual.getStatusCode());
        assertNotNull(actual.getBody());

        String auth0UserId = testData.getTestingUser().getAuth0Id();
        actual = client.createPasswordResetTicket(auth0UserId, redirectUrl);
        assertEquals(200, actual.getStatusCode());
        assertNotNull(actual.getBody());
    }

    @Test
    public void testSetUserGuidForAuth0User() {
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        String auth0MgmtSecret = auth0Config.getString(AUTH0_MGMT_API_CLIENT_SECRET);
        String auth0MgmtClientId = auth0Config.getString(AUTH0_MGMT_API_CLIENT_ID);
        String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);
        var mgmtClient = new Auth0ManagementClient(auth0Domain, auth0MgmtClientId, auth0MgmtSecret);

        String fakeClient = "FakeTestClient" + System.currentTimeMillis();
        String fakeUserGuid = Long.toString(System.currentTimeMillis());
        String testUserAuth0UserId = testData.getTestingUser().getAuth0Id();
        String testUserGuid = testData.getUserGuid();
        User auth0User = mgmtClient.setUserGuidForAuth0User(testUserAuth0UserId, fakeClient, fakeUserGuid);

        try {
            Map<String, Object> appMetadata = auth0User.getAppMetadata();
            assertNotNull(appMetadata);

            Map userGuidMap = (Map) appMetadata.get(Auth0ManagementClient.APP_METADATA_PEPPER_USER_GUIDS);
            assertNotNull(userGuidMap);

            String ddpGuidFromAuth0UserAppMetadata = userGuidMap.get(fakeClient).toString();
            assertEquals(fakeUserGuid, ddpGuidFromAuth0UserAppMetadata);
        } finally {
            auth0User = mgmtClient.removeUserGuidForAuth0User(testUserAuth0UserId, fakeClient);
            Map<String, Object> appMetadata = auth0User.getAppMetadata();
            if (appMetadata != null) {
                Map userGuidMap = (Map) appMetadata.get(Auth0ManagementClient.APP_METADATA_PEPPER_USER_GUIDS);
                if (userGuidMap != null) {
                    LOG.info("Deleted fake client " + fakeClient + " from test user's list of guid.  "
                            + testUserGuid + " has " + userGuidMap.size() + " remaining client/guid mappings.");
                }
            }
        }
    }
}
