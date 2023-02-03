package org.broadinstitute.dsm;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.dsm.security.RSAKeyProviderFactory;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.JWTRouteFilter;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.security.Auth0Util;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

public class JWTRouteFilterTest {
    private static final Logger logger = LoggerFactory.getLogger(JWTRouteFilterTest.class);
    public static final long THIRTY_MIN_IN_SECONDS = 30 * 60 * 60;
    private static final String BEARER = "Bearer";
    private static final String AUTHORIZATION = "Authorization";
    private final String clientId = "https://datadonationplatform.org/cid";
    private final String userId = "https://datadonationplatform.org/uid";
    private final String tenantDomain = "https://datadonationplatform.org/t";
    private static String auth0Domain;
    private static String bspSecret;

    public static long getCurrentUnixUTCTime() {
        return System.currentTimeMillis() / 1000L;
    }

    @BeforeClass
    public static void setup() {
        Config cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File("config/dsm-test-config.conf")));
        auth0Domain = cfg.getString("auth0.domain");
        ;
        bspSecret = cfg.getString(ApplicationConfigConstants.BSP_SECRET);
        ;
    }

    @Ignore("Broken")
    @Test
    public void testGoodTokenWithNullRoles() {
        String secret = "abc";
        String token = BEARER + createTokenWithRoles(secret, getExpirationForTestToken(), JWTRouteFilter.DDP_ROLES_CLAIM, null);
        Request req = EasyMock.mock(Request.class);
        EasyMock.expect(req.headers(AUTHORIZATION)).andReturn(token).once();

        EasyMock.replay(req);
        Assert.assertTrue(new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret));
        EasyMock.verify(req);
    }

    @Test
    public void testTokenWithBadSecret() {
        String secret = "abc";
        String token = BEARER + createTokenWithRoles(secret, getExpirationForTestToken(), JWTRouteFilter.DDP_ROLES_CLAIM, null);
        Request req = EasyMock.mock(Request.class);
        EasyMock.expect(req.headers(AUTHORIZATION)).andReturn(token).once();

        EasyMock.replay(req);
        Assert.assertFalse("Two tokens signed with different secrets should fail",
                new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret));
        EasyMock.verify(req);
    }

    /**
     * Handy utility for generating and sharing a long-lived token for testing
     */
    @Ignore("Broken")
    @Test
    public void printTestToken() throws Exception {
        Config cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File("config/dsm-test-config.conf")));
        String bspSecret = cfg.getString("bsp.secret");
        String ddpSecret = cfg.getString("portal.jwtDdpSecret");
        String monitoringSecret = cfg.getString("portal.jwtMonitoringSecret");

        System.out.println("monitoring token: " + SecurityHelper.createMonitoringToken(monitoringSecret, SecurityHelper.MONITORING_SYSTEM));

        String bspToken = SecurityHelper.createGpToken(bspSecret, getCurrentUnixUTCTime() + THIRTY_MIN_IN_SECONDS, new HashMap<>());
        System.out.println("Token for bsp: " + bspToken);

        String ddpToken = SecurityHelper.createToken(ddpSecret, getCurrentUnixUTCTime() + THIRTY_MIN_IN_SECONDS, new HashMap<>());
        System.out.println("Token for appRoute: " + ddpToken);

        Map<String, JsonElement> ddpConfigurationLookup = new HashMap<>();
        JsonArray array = (JsonArray) (new JsonParser().parse(cfg.getString("ddp")));
        for (JsonElement ddpInfo : array) {
            if (ddpInfo.isJsonObject()) {
                ddpConfigurationLookup.put(
                        ddpInfo.getAsJsonObject().get(ApplicationConfigConstants.INSTANCE_NAME).getAsString().toLowerCase(), ddpInfo);
            }
        }
        JsonElement jsonElement = ddpConfigurationLookup.get("prostate"); //Gen2 DDP you want the token off
        String secret = jsonElement.getAsJsonObject().get("tokenSecret").getAsString();
        String gen2 = SecurityHelper.createToken(secret, getCurrentUnixUTCTime() + THIRTY_MIN_IN_SECONDS, new HashMap<>());
        System.out.println("Token for gen2: " + gen2);

        TestUtil testUtil = TestUtil.newInstance(cfg);
        System.out.println("UI header: ");
        Map<String, String> map = testUtil.buildAuthHeaders();
        for (String header : map.keySet()) {
            System.out.println(header + ": " + map.get(header));
        }

        Auth0Util auth0Util = new Auth0Util(cfg.getString("auth0.account"), cfg.getStringList("auth0.connections"),
                cfg.getString("auth0.clientKey"), cfg.getString("auth0.secret"),
                cfg.getString("auth0.mgtKey"), cfg.getString("auth0.mgtSecret"), cfg.getString("auth0.mgtApiUrl"),
                cfg.getString("auth0.audience"));
        System.out.println("Token for Pepper: " + auth0Util.getAccessToken());

        RSAKeyProvider keyProvider = null;
        try {
            JwkProvider jwkProvider = new JwkProviderBuilder(cfg.getString("auth0.domain")).build();
            keyProvider = RSAKeyProviderFactory.createRSAKeyProviderWithPrivateKeyOnly(jwkProvider);
            Algorithm algorithm = Algorithm.RSA256(keyProvider);
            try {
                JWTCreator.Builder builder = com.auth0.jwt.JWT.create();
                System.out.println("User Token: " + builder.sign(algorithm));
            } catch (Exception e) {
                throw new RuntimeException("Couldn't create token " + e);
            }
        } catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void testNoToken() {
        Request req = EasyMock.mock(Request.class);
        EasyMock.expect(req.headers(AUTHORIZATION)).andReturn(null).once();

        EasyMock.replay(req);
        Assert.assertFalse("Empty token should fail", new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret));
        EasyMock.verify(req);
    }

    @Test
    public void testCorruptToken() {
        String corruptToken = createTokenWithRoles("secret", getExpirationForTestToken(), JWTRouteFilter.DDP_ROLES_CLAIM, null);
        corruptToken = corruptToken.substring(1);
        Request req = EasyMock.mock(Request.class);
        EasyMock.expect(req.headers(AUTHORIZATION)).andReturn(corruptToken).once();

        EasyMock.replay(req);
        Assert.assertFalse("Corrupt token should fail", new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret));
        EasyMock.verify(req);
    }

    @Ignore("Broken")
    @Test
    public void testGoodTokenWithSpecificRole() {
        String secret = "abc";
        String role = "foo";
        String token =
                BEARER + createTokenWithRoles(secret, getExpirationForTestToken(), JWTRouteFilter.DDP_ROLES_CLAIM, Arrays.asList(role));
        Request req = EasyMock.mock(Request.class);
        EasyMock.expect(req.headers(AUTHORIZATION)).andReturn(token).times(2);

        EasyMock.replay(req);
        Assert.assertTrue("Checking signature without a role should pass.",
                new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret));
        Assert.assertFalse("Checking signature with the wrong role should fail",
                new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret));
        EasyMock.verify(req);
    }

    @Test
    public void testTokenExpiration() {
        String secret = "secret";
        String token = BEARER + createTokenWithRoles(secret, getCurrentUnixUTCTime(), JWTRouteFilter.DDP_ROLES_CLAIM, null);
        Request req = EasyMock.mock(Request.class);
        EasyMock.expect(req.headers(AUTHORIZATION)).andReturn(token).once();
        EasyMock.replay(req);
        try {
            Thread.sleep(1000);
            Assert.assertFalse("Token is expired, should not be considered valid",
                    new JWTRouteFilter(auth0Domain).isAccessAllowed(req, false, bspSecret));
        } catch (InterruptedException e) {
            Assert.fail("Sleep interrupted, cannot wait for token to expire.");
        }
        EasyMock.verify(req);
    }

    @Ignore("Broken")
    @Test
    public void checkTokenClaims() {
        Config cfg = ConfigFactory.load();
        //secrets from vault in a config file
        // cfg = cfg.withFallback(ConfigFactory.parseFile(new File(System.getenv("TEST_CONFIG_FILE"))));
        Map<String, String> claims = new HashMap<>();
        claims.put("USER_ID", "1");
        String jwtToken =
                new SecurityHelper().createToken("secret", getCurrentUnixUTCTime() + (System.currentTimeMillis() / 1000) + (60 * 5),
                        claims);
        Map<String, Claim> claimsFromToken = SecurityHelper.verifyAndGetClaims("secret", jwtToken, cfg.getString("auth0.account"));
        String userId = claimsFromToken.get("USER_ID").asString();
        Assert.assertNotNull(userId);
        Assert.assertEquals("1", userId);
    }

    private long getExpirationForTestToken() {
        return getCurrentUnixUTCTime() + (60);
    }

    private String createTokenWithRoles(String secret, long invalidAfter, String rolesKey, Collection<String> roles) {
        try {
            Date dateSoon = new Date(invalidAfter * 1000);
            JWTCreator.Builder builder = com.auth0.jwt.JWT.create();
            builder.withExpiresAt(dateSoon);
            builder.withClaim(rolesKey, new Gson().toJson(roles, ArrayList.class));
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return builder.sign(algorithm);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't create token " + e);
        }
    }

    public static DecodedJWT verifyDDPToken(String jwt, JwkProvider jwkProvider) {
        DecodedJWT validToken;
        RSAKeyProvider keyProvider;
        try {
            keyProvider = RSAKeyProviderFactory.createRSAKeyProviderWithPrivateKeyOnly(jwkProvider);
        } catch (Exception e) {
            logger.error("Error creating RSAKeyProvider", e);
            throw (e);
        }

        try {
            validToken = JWT.require(Algorithm.RSA256(keyProvider)).acceptLeeway(10).build().verify(jwt);
        } catch (TokenExpiredException e) {
            // TokenExpired is one of the benign variants of JWTVerificationException that the `verify()` method throws.
            logger.warn("Expired token: {}", jwt);
            throw e;
        } catch (Exception e) {
            logger.error("Could not verify token {}", jwt, e);
            throw (e);
        }
        return validToken;
    }

    @Ignore("Not a real test. This a utility")
    @Test
    public void test() {
        Map<String, JwkProvider> jwkProviderMap = new HashMap();
        String jwt = "<COPY_TOKEN_HERE>";
        // We need to get the Auth0ClientId *before* verification so that we can verify see: Chicken/Egg
        DecodedJWT decodedJwt = JWT.decode(jwt);
        String auth0ClientId = decodedJwt.getClaim(clientId).asString();
        String auth0Domain = decodedJwt.getClaim(tenantDomain).asString();
        JwkProvider jwkProvider = jwkProviderMap.getOrDefault(auth0ClientId, null);
        if (jwkProvider == null) {

            jwkProvider = new JwkProviderBuilder(auth0Domain).cached(100, 3L, MINUTES).build();
            jwkProviderMap.put(auth0ClientId, jwkProvider);
        }

        DecodedJWT validToken = verifyDDPToken(jwt, jwkProvider);
        String ddpUserGuid = validToken.getClaim(userId).asString();
        Assert.assertNotNull(ddpUserGuid);
    }

}
