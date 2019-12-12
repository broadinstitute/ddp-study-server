package org.broadinstitute.ddp.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.route.DsmRouteTest;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.junit.Before;
import org.junit.Test;

public class DsmAuthFilterTest extends DsmRouteTest {

    private DsmAuthFilter filter;
    private String dsmClientId;

    @Before
    public void setup() {
        Config auth0Config = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0);
        dsmClientId = auth0Config.getString(ConfigFile.AUTH0_DSM_CLIENT_ID);
        String auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);

        filter = new DsmAuthFilter(dsmClientId, auth0Domain);
    }

    @Test
    public void testObtainValidToken() {
        DecodedJWT token = filter.decodeToken(dsmClientAccessToken);
        assertNotNull(token);
        Map<String, Claim> claims = token.getClaims();
        Claim audClaim = claims.get("aud");
        assertNotNull(audClaim);
        Claim subClaim = claims.get("sub");
        String clientId = subClaim.asString().substring(0, subClaim.asString().indexOf("@clients"));
        assertEquals(dsmClientId, clientId);
    }

    @Test
    public void testValidTokenAndValidClient() {
        assertTrue(filter.isTokenValueValid(dsmClientAccessToken));
    }

    @Test
    public void testInvalidToken() {
        String token = "ThisIsAnInvalidToken";
        assertFalse(filter.isTokenValueValid(token));
    }

    @Test
    public void testValidTokenWithInvalidClient() {
        TransactionWrapper.withTxn(handle -> {
            handle.createUpdate("UPDATE client SET is_revoked = 1 where client_name = \'" + TEST_DSM_CLIENT_NAME + "\'").execute();
            return null;
        });
        boolean result = filter.isTokenValueValid(dsmClientAccessToken);
        TransactionWrapper.withTxn(handle -> {
            handle.createUpdate("UPDATE client SET is_revoked = 0 where client_name = \'" + TEST_DSM_CLIENT_NAME + "\'").execute();
            return null;
        });
        assertFalse(result);

    }
}
