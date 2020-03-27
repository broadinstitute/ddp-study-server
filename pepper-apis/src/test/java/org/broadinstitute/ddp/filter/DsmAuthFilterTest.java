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
    private String auth0Domain;

    @Before
    public void setup() {
        Config auth0Config = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0);
        dsmClientId = auth0Config.getString(ConfigFile.AUTH0_DSM_CLIENT_ID);
        auth0Domain = auth0Config.getString(ConfigFile.DOMAIN);

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
        String updateIsRevokedQueryTemplate = "UPDATE client c JOIN auth0_tenant t ON c.auth0_tenant_id = t.auth0_tenant_id "
                + " SET c.is_revoked = %d WHERE c.auth0_client_id = %s AND t.auth0_domain = %s";

        TransactionWrapper.withTxn(handle -> {
            String query = String.format(updateIsRevokedQueryTemplate, 1, dsmClientId, auth0Domain);
            handle.createUpdate(query).execute();
            return null;
        });
        boolean result = filter.isTokenValueValid(dsmClientAccessToken);
        TransactionWrapper.withTxn(handle -> {
            String query = String.format(updateIsRevokedQueryTemplate, 0, dsmClientId, auth0Domain);
            handle.createUpdate(query).execute();
            return null;
        });
        assertFalse(result);

    }
}
