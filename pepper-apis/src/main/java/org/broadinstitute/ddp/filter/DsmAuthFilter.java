package org.broadinstitute.ddp.filter;

import static org.broadinstitute.ddp.constants.RouteConstants.AUTHORIZATION;
import static spark.Spark.halt;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.security.JWTConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * Authentication filter designed to be applied to DSM-specific routes.
 * Checks JWT token from non-interactive client in Auth0 .
 *
 * @see <a href="https://auth0.com/docs/architecture-scenarios/application/server-api/part-1#client-credentials-grant">Auth0 Docs: Server + API: Solution Overview</a>
 */
public class DsmAuthFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(DsmAuthFilter.class);
    private final JwkProvider jwkProvider;
    private String dsmAuth0ClientId;

    /**
     * Build the filter.
     *
     * @param dsmAuth0ClientId the Auth0 client id
     * @param dsmAuth0Domain   the Auth0 domain for DSM
     */
    public DsmAuthFilter(String dsmAuth0ClientId, String dsmAuth0Domain) {
        this.jwkProvider = new JwkProviderBuilder(dsmAuth0Domain).cached(100, 3L, TimeUnit.HOURS).build();
        this.dsmAuth0ClientId = dsmAuth0ClientId;
    }

    @Override
    public void handle(Request request, Response response) {
        String authorizationHeaderValue = request.headers(AUTHORIZATION);
        if (StringUtils.isNotBlank(authorizationHeaderValue)) {
            if (isAuthorizationHeaderValid(authorizationHeaderValue)) {
                return;
            } else {
                LOG.error("Did not find a valid token in Authorization header: {}", authorizationHeaderValue);
            }
        } else {
            LOG.error("Missing {} header on request with URL: {}", AUTHORIZATION, request.url());
        }
        throw halt(401);

    }

    boolean isAuthorizationHeaderValid(String authorizationHeader) {
        String tokenValue = JWTConverter.extractEncodedJwtFromHeader(authorizationHeader);
        return isTokenValueValid(tokenValue);
    }

    boolean isTokenValueValid(String tokenValue) {
        if (tokenValue == null) {
            LOG.error("Did not find token within Authorization header");
            return false;
        }
        DecodedJWT jwt;
        try {
            jwt = JWTConverter.verifyDDPToken(tokenValue, this.jwkProvider);
        } catch (Exception e) {
            LOG.error("Could not decode token", e);
            return false;
        }
        Optional<String> tokenClientId = extractClientIdFromToken(jwt);
        if (!tokenClientId.isPresent()) {
            LOG.error("Could not extract Auth0 client id from token");
            return false;
        }
        String domain = jwt.getIssuer();
        if (StringUtils.isBlank(domain)) {
            LOG.error("Could not extract Auth0 domain from token");
            return false;
        }

        return (isTokenClientValid(tokenClientId.get(), domain));
    }

    /**
     * Get the clientId reported by the token.
     *
     * @param jwt the decoded token object
     * @return the clientId extracted from token "sub" claim
     */
    private Optional<String> extractClientIdFromToken(DecodedJWT jwt) {
        //sub claim contains the clientId with a suffix. Extract the client id from it
        return Optional.ofNullable(jwt.getClaim("sub"))
                .map(claim -> claim.asString().substring(0, claim.asString().indexOf("@clients")));
    }

    /**
     * For token client to be valid it has to exist, be the one that we expect, and it has to be still active.
     *
     * @param tokenClientId clientId extracted from token
     * @param auth0Domain   the auth0 domain, which is the issuer JWT claim
     * @return whether valid or not
     */
    private boolean isTokenClientValid(String tokenClientId, String auth0Domain) {
        if (!(tokenClientId.equals(this.dsmAuth0ClientId))) {
            LOG.error("clientId in token did not match expected DSM clientId in configuration. Token clientId {}",
                    tokenClientId);
            return false;
        }
        boolean isClientActive = TransactionWrapper.withTxn(handle -> {
            //if it is not active, we cannot get a config
            return (handle.attach(ClientDao.class).isAuth0ClientActive(tokenClientId, auth0Domain));
        });
        if (!isClientActive) {
            LOG.error("A configuration for the DSM clientId does not exist or it is not active");
        }
        return isClientActive;

    }

    DecodedJWT decodeToken(String token) {
        return JWTConverter.verifyDDPToken(token, this.jwkProvider);
    }

}
