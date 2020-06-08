package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import com.auth0.json.mgmt.Connection;
import com.auth0.json.mgmt.users.User;
import org.broadinstitute.ddp.client.ApiResult;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.model.study.PasswordPolicy;
import org.junit.Test;

public class Auth0ServiceTest {

    @Test
    public void testFindClientDBConnection() {
        var mockAuth0Client = mock(Auth0ManagementClient.class);
        var dbConnection = new Connection("db-name", Auth0ManagementClient.DB_CONNECTION_STRATEGY);
        doReturn(ApiResult.ok(200, List.of(dbConnection)))
                .when(mockAuth0Client).listClientConnections(any());
        var service = new Auth0Service(mockAuth0Client);
        var actual = service.findClientDBConnection("client-id");
        assertEquals(dbConnection, actual);
    }

    @Test
    public void testExtractPasswordPolicy() {
        var dbConnection = new Connection("db-name", Auth0ManagementClient.DB_CONNECTION_STRATEGY);
        dbConnection.setOptions(Map.of(
                Auth0ManagementClient.KEY_PASSWORD_COMPLEXITY_OPTIONS, Map.of(Auth0ManagementClient.KEY_MIN_LENGTH, 25),
                Auth0ManagementClient.KEY_PASSWORD_POLICY, PasswordPolicy.PolicyType.GOOD.name()));

        var service = new Auth0Service(null);
        var actual = service.extractPasswordPolicy(dbConnection);
        assertNotNull(actual);

        assertEquals(PasswordPolicy.PolicyType.GOOD, actual.getType());
        assertEquals(25, actual.getMinLength());
    }

    @Test
    public void testGeneratePassword() {
        var policy = PasswordPolicy.excellent(Auth0Service.DEFAULT_PASSWORD_LENGTH + 1);
        var service = new Auth0Service(null);
        String pwd = service.generatePassword(policy);
        assertNotNull(pwd);
        assertEquals(Auth0Service.DEFAULT_PASSWORD_LENGTH + 1, pwd.length());
    }

    @Test
    public void testGeneratePasswordResetRedirectUrl() {
        var service = new Auth0Service(null, "http://localhost");
        String url = service.generatePasswordResetRedirectUrl("foo", "bar");
        assertEquals("http://localhost/pepper/v1/post-password-reset?clientId=foo&domain=bar", url);
    }

    @Test
    public void testCreateUserWithPasswordTicket() {
        var dbConnection = new Connection("db-name", Auth0ManagementClient.DB_CONNECTION_STRATEGY);
        var user = new User(dbConnection.getName());
        user.setId("fake-auth0-id");

        var mockAuth0Client = mock(Auth0ManagementClient.class);
        doReturn(ApiResult.ok(200, user)).when(mockAuth0Client).createAuth0User(any(), any(), any());
        doReturn(ApiResult.ok(200, "ticket")).when(mockAuth0Client).createPasswordResetTicket(any(), any());

        var service = new Auth0Service(mockAuth0Client);
        var actual = service.createUserWithPasswordTicket(dbConnection, "email", "redirect");
        assertNotNull(actual);
        assertEquals("fake-auth0-id", actual.getUser().getId());
        assertEquals("ticket", actual.getTicket());
    }
}
