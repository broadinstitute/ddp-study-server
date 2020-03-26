package org.broadinstitute.ddp.security;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDao;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.exception.DDPTokenException;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JWTConverter {

    private static final Logger LOG = LoggerFactory.getLogger(JWTConverter.class);
    private static final String DEFAULT_ISO_LANGUAGE_CODE = "en";
    private Map<String, JwkProvider> jwkProviderMap = new HashMap<>(); // Map of Auth0ClientIds -> jwkProviders
    private UserDao userDao;

    public JWTConverter(UserDao userDao) {
        this.userDao = userDao;
    }

    public static JwkProvider defaultProvider(String auth0Domain) {
        return new JwkProviderBuilder(auth0Domain).cached(100, 3L, TimeUnit.HOURS).build();
    }

    /**
     * Verifies the given token by checking the signature with the
     * given jwk provider
     *
     * @param jwt         the token to verify
     * @param jwkProvider the provider of jwk data, typically auth0's jwk endpoint
     * @return a verified, decoded JWT
     * @throws DDPTokenException if issues with key provider
     * @throws com.auth0.jwt.exceptions.JWTVerificationException if issues with jwt, e.g. token expired, invalid claims, etc.
     */
    public static DecodedJWT verifyDDPToken(String jwt, JwkProvider jwkProvider) {
        DecodedJWT validToken;
        RSAKeyProvider keyProvider;
        try {
            keyProvider = RSAKeyProviderFactory.createRSAKeyProviderWithPrivateKeyOnly(jwkProvider);
        } catch (DDPTokenException e) {
            LOG.error("Error creating RSAKeyProvider", e);
            throw (e);
        }

        try {
            validToken = JWT.require(Algorithm.RSA256(keyProvider)).acceptLeeway(10).build().verify(jwt);
        } catch (TokenExpiredException e) {
            // TokenExpired is one of the benign variants of JWTVerificationException that the `verify()` method throws.
            LOG.warn("Expired token: {}", jwt);
            throw e;
        } catch (Exception e) {
            LOG.error("Could not verify token {}", jwt, e);
            throw (e);
        }
        return validToken;
    }

    /**
     * Extract the expected token string from the Authentication header.
     *
     * @param authHeader the header string
     * @return the token string
     */
    public static String extractEncodedJwtFromHeader(String authHeader) {
        if (StringUtils.isNotBlank(authHeader)) {
            int jwtStartIndex = authHeader.indexOf(RouteConstants.Header.BEARER);
            if (jwtStartIndex > -1) {
                return authHeader.substring(jwtStartIndex + RouteConstants.Header.BEARER.length());
            }
        }
        return null;
    }

    private String getPreferredLanguageCodeForUser(Handle handle, String userGuid) {
        JdbiUser userDao = handle.attach(JdbiUser.class);
        Long userId = userDao.getUserIdByGuid(userGuid);
        JdbiProfile userProfileDao = handle.attach(JdbiProfile.class);
        UserProfileDto userProfileDto = userProfileDao.getUserProfileByUserId(userId);
        String preferredIsoLanguageCode;
        if (userProfileDto != null && userProfileDto.getPreferredLanguageCode() != null) {
            preferredIsoLanguageCode = userProfileDto.getPreferredLanguageCode();
            LOG.info("The preferred language code for the user with GUID {} is '{}'",
                    userGuid, preferredIsoLanguageCode);
        } else {
            LOG.info("There is no preferred language code for the user with GUID {}, using a default one ('{}')",
                    userGuid, DEFAULT_ISO_LANGUAGE_CODE);
            preferredIsoLanguageCode = DEFAULT_ISO_LANGUAGE_CODE;
        }
        return preferredIsoLanguageCode;
    }

    private DDPAuth convertJWT(String jwt) {
        DDPAuth ddpAuth =
                TransactionWrapper.withTxn(handle -> {
                    DDPAuth txnDdpAuth = null;
                    // We need to get the Auth0ClientId *before* verification so that we can verify see: Chicken/Egg
                    DecodedJWT decodedJwt = JWT.decode(jwt);
                    String auth0ClientId = decodedJwt.getClaim(Auth0Constants.DDP_CLIENT_CLAIM).asString();
                    String auth0Domain = decodedJwt.getClaim(Auth0Constants.DDP_TENANT_CLAIM).asString();
                    JwkProvider jwkProvider = jwkProviderMap.getOrDefault(auth0ClientId, null);
                    if (jwkProvider == null) {
                        ClientDao clientDao = handle.attach(ClientDao.class);
                        StudyClientConfiguration configuration = clientDao.getConfiguration(auth0ClientId,
                                auth0Domain);
                        if (configuration == null) {
                            throw new DDPTokenException("Could not find configuration with auth0clientId: " + auth0ClientId
                                    + " auth0Domain: " + auth0Domain);
                        }
                        jwkProvider = new JwkProviderBuilder(configuration.getAuth0Domain()).cached(100, 3L, TimeUnit.MINUTES).build();
                        jwkProviderMap.put(auth0ClientId, jwkProvider);
                    }

                    try {
                        DecodedJWT validToken = verifyDDPToken(jwt, jwkProvider);
                        String ddpUserGuid = validToken.getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString();
                        UserPermissions userPermissions = userDao.queryUserPermissions(
                                handle,
                                ddpUserGuid,
                                auth0ClientId,
                                auth0Domain
                        );
                        String preferredLanguage = getPreferredLanguageCodeForUser(handle, ddpUserGuid);
                        txnDdpAuth = new DDPAuth(auth0ClientId, ddpUserGuid, jwt, userPermissions, preferredLanguage);
                    } catch (Exception e) {
                        LOG.warn("Could not verify token. User "
                                + decodedJwt.getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString()
                                + " tried to authenticate against client auth0clientId "
                                + auth0ClientId);
                        throw e;
                    }

                    return txnDdpAuth;
                });

        return ddpAuth;
    }

    /**
     * Parses the {@link RouteConstants.Header#AUTHORIZATION authorization} {@link RouteConstants.Header#BEARER bearer}
     * header, validates the JWT, and converts it into
     * {@link DDPAuth a ddp auth object}.
     */
    public DDPAuth convertJWTFromHeader(String authHeader) {
        DDPAuth ddpAuth = new DDPAuth();
        String jwt = extractEncodedJwtFromHeader(authHeader);
        if (jwt != null) {
            ddpAuth = convertJWT(jwt);
        }
        return ddpAuth;
    }
}
