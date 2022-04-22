package org.broadinstitute.ddp.util;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.ArrayList;
import java.util.List;

import com.auth0.json.mgmt.users.User;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.MockServerRule;

@Slf4j
public class Auth0UtilMockedTest {
    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    private MockServerClient mockServerClient;

    private Auth0Util auth0Util = null;

    private final int fakeExpiration = 99999;
    private final String fakeIdToken = "not a real id token";
    private final String fakeAccessToken = "not a real access token";

    private final Gson gson = new Gson();

    @Before
    public void setupMockAuth0Server() {
        if (auth0Util == null) {
            auth0Util = new Auth0Util("http://localhost:" + mockServerRule.getPort() + "/");
        }
    }

    @Test
    public void testGoodRefreshToken() {
        mockServerClient = mockServerClient.reset();
        Auth0Util.RefreshTokenResponse mockResponse = new Auth0Util.RefreshTokenResponse(
                fakeIdToken, fakeAccessToken, fakeExpiration);

        mockServerClient.when(request().withPath("/" + Auth0Util.REFRESH_ENDPOINT))
                .respond(response()
                        .withStatusCode(200)
                        .withBody(gson.toJson(mockResponse)));
        Auth0Util.RefreshTokenResponse refreshTokenResponse = auth0Util.refreshToken("ignored", "ignored", "ignored");

        Assert.assertEquals(fakeIdToken, refreshTokenResponse.getIdToken());
        Assert.assertEquals(fakeAccessToken, refreshTokenResponse.getAccessToken());
        Assert.assertEquals(fakeExpiration, refreshTokenResponse.getExpiresIn());
        Assert.assertTrue(StringUtils.isBlank(refreshTokenResponse.getRefreshToken()));
    }

    @Test
    public void testAuth0ErrorHandling() {
        mockServerClient = mockServerClient.reset();
        mockServerClient.when(request().withPath("/" + Auth0Util.REFRESH_ENDPOINT))
                .respond(response()
                        .withStatusCode(401));

        try {
            auth0Util.refreshToken("ignored", "ignored", "ignored");
            Assert.fail("Auth0Util should have failed when mock auth0 endpoint return non-2xx");
        } catch (Exception e) {
            log.info("ignoring expected exception from mock", e);
        }
    }

    @Test
    public void testGoodExchangeFromCode() {
        mockServerClient = mockServerClient.reset();

        String client = "client";
        String code = "code";
        String redirectUri = "redirectUri";
        String secret = "secret";
        Auth0Util.RequestRefreshTokenPayload payload =
                new Auth0Util.RequestRefreshTokenPayload(client, secret, code, redirectUri);
        Auth0Util.RefreshTokenResponse mockResponse =
                new Auth0Util.RefreshTokenResponse(fakeIdToken, fakeAccessToken, fakeExpiration);

        mockServerClient.when(request().withPath("/" + Auth0Util.REFRESH_ENDPOINT).withBody(gson.toJson(payload)))
                .respond(response()
                        .withStatusCode(200)
                        .withBody(gson.toJson(mockResponse)));

        Auth0Util.RefreshTokenResponse refreshTokenResponse =
                auth0Util.getRefreshTokenFromCode(code, client, secret, redirectUri);

        Assert.assertEquals(fakeIdToken, refreshTokenResponse.getIdToken());
        Assert.assertEquals(fakeAccessToken, refreshTokenResponse.getAccessToken());
        Assert.assertEquals(fakeExpiration, refreshTokenResponse.getExpiresIn());
        Assert.assertTrue(StringUtils.isBlank(refreshTokenResponse.getRefreshToken()));
    }

    @Test
    public void testListUsersByEmail() throws Exception {
        mockServerClient = mockServerClient.reset();
        List<User> mockResponse = new ArrayList<>();
        String fakeConn = "fake connection";
        User user = new User(fakeConn);
        String fakeEmail = "fakemeail@datadonationplatform.org";
        user.setEmail(fakeEmail);
        mockResponse.add(user);

        mockServerClient.when(request().withPath("/api/v2/users-by-email"))
                .respond(response()
                        .withStatusCode(200)
                        .withBody(gson.toJson(mockResponse)));
        List<User> users = auth0Util.getAuth0UsersByEmail(fakeEmail, fakeIdToken);
        Assert.assertNotNull(users);
        Assert.assertEquals(1, users.size());
        Assert.assertEquals(fakeEmail, users.get(0).getEmail());
    }
}
