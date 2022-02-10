package org.broadinstitute.dsm;

import org.apache.http.HttpResponse;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.lddp.security.Auth0Util;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationTest {
    @Test
    public void testAuthentication() throws IOException {
        String auth0Token = "";
        String user_id = "NMRUIZFATUIXIVMHJE9K";
        String operatorGUID = "C2JYXJUUBC2KP9R10G1U";
        int cookieAgeInSeconds = 60;
//        Map<String, String> claims = new HashMap<>();
//        Auth0Util auth0Util = new Auth0Util(cfg.getString(ApplicationConfigConstants.AUTH0_ACCOUNT),
//                cfg.getStringList(ApplicationConfigConstants.AUTH0_CONNECTIONS),
//                cfg.getBoolean(ApplicationConfigConstants.AUTH0_IS_BASE_64_ENCODED),
//                cfg.getString(ApplicationConfigConstants.AUTH0_CLIENT_KEY),
//                cfg.getString(ApplicationConfigConstants.AUTH0_SECRET),
//                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_KEY),
//                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_SECRET),
//                cfg.getString(ApplicationConfigConstants.AUTH0_MGT_API_URL),
//                false, cfg.getString(ApplicationConfigConstants.AUTH0_AUDIENCE));
//         final String clientId = "https://datadonationplatform.org/cid";
//         final String userId = "https://datadonationplatform.org/cid";
//         final String tenantDomain = "https://datadonationplatform.org/t";
//        auth0Util.getClaimValue(auth0Token, tenantDomain).ifPresent( claim -> claims.put(tenantDomain, claim.asString()));
//        auth0Util.getClaimValue(auth0Token, clientId).ifPresent( claim -> claims.put(clientId, claim.asString()));
//        claims.put("https://datadonationplatform.org/uid", "C2JYXJUUBC2KP9R10G1U");
//        String jwtToken = new SecurityHelper().createToken(auth0Token, cookieAgeInSeconds + (System.currentTimeMillis() / 1000) + (60 * 5),
//                claims);

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + auth0Token);
        String reqAddress = "https://pepper-dev.datadonationplatform.org/pepper/v1/user/"+user_id+"/studies/cmi-pancan/activities";
        HttpResponse response = TestUtil.performGet(reqAddress, "", authHeaders).returnResponse();
        System.out.println(response.toString());
    }
}
