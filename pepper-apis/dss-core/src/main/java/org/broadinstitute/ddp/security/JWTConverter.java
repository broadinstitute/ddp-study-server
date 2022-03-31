package org.broadinstitute.ddp.security;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.IdToCacheKeyCollectionMapper;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.exception.DDPTokenException;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JWTConverter {

    private static final Logger LOG = LoggerFactory.getLogger(JWTConverter.class);
    private static final String DEFAULT_ISO_LANGUAGE_CODE = "en";
    private Map<String, JwkProvider> jwkProviderMap = new HashMap<>(); // Map of Auth0ClientIds -> jwkProviders
    private Cache<String, DDPAuth> jwtToDDPAuthCache;
    private Cache<Long, Set<String>> userIdToJwtCache;

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
     * @throws DDPTokenException                                 if issues with key provider
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
            validToken = JWT.require(Algorithm.RSA256(keyProvider)).acceptLeeway(1000).build().verify(jwt);
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

    public JWTConverter() {
        super();
        initializeCaching();
    }

    void resetCaching() {
        jwtToDDPAuthCache.clear();
        userIdToJwtCache.clear();
    }

    private void initializeCaching() {
        userIdToJwtCache = CacheService.getInstance().getOrCreateCache("userIdToJwtCache",
                new Duration(MINUTES, 11), this);


        jwtToDDPAuthCache = CacheService.getInstance().getOrCreateCache("jwtToDDPAuth",
                new Duration(MINUTES, 10),
                (IdToCacheKeyCollectionMapper) (id, handle) -> userIdToJwtCache.get(id), ModelChangeType.USER, this);
    }

    private String getPreferredLanguageCodeForUser(UserProfile userProfile, String userGuid) {
        String preferredIsoLanguageCode = DEFAULT_ISO_LANGUAGE_CODE;
        if (userProfile != null) {
            if (userProfile.getPreferredLangCode() != null) {
                preferredIsoLanguageCode = userProfile.getPreferredLangCode();
                LOG.info("The preferred language code for the user with GUID {} is '{}'",
                        userGuid, preferredIsoLanguageCode);
            }
        }
        return preferredIsoLanguageCode;
    }

    private String getPreferredLanguageCodeForUser(Handle handle, String userGuid) {
        UserProfile userProfile = findUserProfile(handle, userGuid);
        String preferredIsoLanguageCode = DEFAULT_ISO_LANGUAGE_CODE;
        if (userProfile != null) {
            if (userProfile.getPreferredLangCode() != null) {
                preferredIsoLanguageCode = userProfile.getPreferredLangCode();
                LOG.info("The preferred language code for the user with GUID {} is '{}'",
                        userGuid, preferredIsoLanguageCode);
            }
        }
        return preferredIsoLanguageCode;
    }

    private UserProfile findUserProfile(Handle handle, String userGuid) {
        return handle.attach(UserProfileDao.class).findProfileByUserGuid(userGuid).orElse(null);
    }

    private DDPAuth convertJWT(String jwt) {
        DDPAuth cachedAuth = jwtToDDPAuthCache.get(jwt);
        if (cachedAuth != null) {
            LOG.info("Auth found in cache");
            TransactionWrapper.useTxn(handle -> {
                String preferrededLanguage = getPreferredLanguageCodeForUser(handle, cachedAuth.getOperator());
                cachedAuth.setPreferredLanguage(preferrededLanguage);

            });
            return cachedAuth;
        }
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
                        jwkProvider = new JwkProviderBuilder(configuration.getAuth0Domain()).cached(100, 3L, MINUTES).build();
                        jwkProviderMap.put(auth0ClientId, jwkProvider);
                    }

                    UserProfile userProfile;
                    Long userId;
                    try {
                        DecodedJWT validToken = verifyDDPToken(jwt, jwkProvider);
                        String ddpUserGuid = validToken.getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString();
                        UserPermissions userPermissions = handle.attach(AuthDao.class)
                                .findUserPermissions(ddpUserGuid, auth0ClientId, auth0Domain);
                        userProfile = findUserProfile(handle, ddpUserGuid);

                        if (userProfile == null) {
                            Optional<User> user = handle.attach(UserDao.class).findUserByGuid(ddpUserGuid);
                            userId = user.isPresent() ? user.get().getId() : null;
                        } else {
                            userId = userProfile.getUserId();
                        }
                        String preferredLanguage = getPreferredLanguageCodeForUser(userProfile, ddpUserGuid);
                        txnDdpAuth = new DDPAuth(auth0ClientId, ddpUserGuid, jwt, userPermissions, preferredLanguage);
                    } catch (Exception e) {
                        LOG.warn("Could not verify token. User "
                                + decodedJwt.getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString()
                                + " tried to authenticate against client auth0clientId "
                                + auth0ClientId);
                        throw e;
                    }
                    if (userId != null) {
                        Set<String> existingJwts = userIdToJwtCache.get(userId);
                        if (existingJwts == null) {
                            userIdToJwtCache.put(userId, Set.of(jwt));
                        } else {
                            Set<String> newSet = new HashSet(existingJwts);
                            newSet.add(jwt);
                            userIdToJwtCache.put(userId, newSet);
                        }
                        jwtToDDPAuthCache.put(jwt, txnDdpAuth);
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
        DDPAuth ddpAuth;
        String jwt = extractEncodedJwtFromHeader(authHeader);
        if (jwt != null) {
            ddpAuth = convertJWT(jwt);
        } else {
            ddpAuth = new DDPAuth();
            ddpAuth.setPreferredLanguage(DEFAULT_ISO_LANGUAGE_CODE);
        }
        return ddpAuth;
    }
}
