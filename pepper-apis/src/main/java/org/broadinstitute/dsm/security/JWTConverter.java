package org.broadinstitute.dsm.security;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JWTConverter {

    private static final Logger logger = LoggerFactory.getLogger(JWTConverter.class);

    /**
     * Verifies the given token by checking the signature with the
     * given jwk provider
     *
     * @param jwt         the token to verify
     * @param auth0Domain auth0 domain
     * @return a verified, decoded JWT
     */
    public static DecodedJWT verifyDDPToken(String jwt, String auth0Domain) {
        RSAKeyProvider keyProvider = null;
        DecodedJWT validToken = null;
        try {
            JwkProvider jwkProvider = new JwkProviderBuilder(auth0Domain).build();
            keyProvider = RSAKeyProviderFactory.createRSAKeyProviderWithPrivateKeyOnly(jwkProvider);
        }
        catch (Exception e) {
            logger.warn("Could not verify token {} due to jwk error", jwt);
        }
        if (keyProvider != null) {
            try {
                validToken = JWT.require(Algorithm.RSA256(keyProvider)).acceptLeeway(10).build().verify(jwt);
            }
            catch (Exception e) {
                logger.warn("Could not verify token {}", jwt, e);
            }
        }
        return validToken;
    }
}
