package org.broadinstitute.ddp.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.List;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.json.mgmt.Connection;
import org.junit.Before;
import org.junit.Test;

public class Auth0ManagementClientTest {

    private ManagementAPI mockApi;
    private Auth0ManagementClient client;

    @Before
    public void setup() {
        mockApi = mock(ManagementAPI.class);
        client = spy(new Auth0ManagementClient("http://localhost", "id", "secret", mockApi));
        doReturn("fake-token").when(client).getToken();
    }

    @Test
    public void testListClientConnections() {
        Connection conn1 = new Connection("foo", "strategy");
        conn1.setEnabledClients(List.of("client"));
        Connection conn2 = new Connection("bar", "strategy");
        conn2.setEnabledClients(List.of());
        doReturn(ApiResult.ok(200, List.of(conn1, conn2))).when(client).listConnections();

        var actual = client.listClientConnections("client");
        assertNotNull(actual);
        assertTrue(actual.hasBody());
        assertEquals(1, actual.getBody().size());
        assertEquals("foo", actual.getBody().get(0).getName());
    }
}
