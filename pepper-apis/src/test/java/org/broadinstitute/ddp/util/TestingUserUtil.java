package org.broadinstitute.ddp.util;

import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_CLIENT_ID;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_CLIENT_NAME;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_SECRET;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_ADMIN_EMAIL;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_ADMIN_PASSWORD;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_EMAIL;
import static org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing.AUTH0_TEST_PASSWORD;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.auth0.client.auth.AuthAPI;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.net.AuthRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.security.JWTConverter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestingUserUtil {

    private static final Logger LOG = LoggerFactory.getLogger(TestingUserUtil.class);

    /**
     * Creates a totally new user that hopefully has not been
     * used before, logs it in, and returns information about the
     * user as well as a token that can be used for pepper routes
     */
    public static Auth0Util.TestingUser createAndLoginNewTestUser(Handle handle,
                                                                  String auth0Domain,
                                                                  String auth0ClientName,
                                                                  String auth0ClientId,
                                                                  String auth0Secret,
                                                                  String studyGuid) throws Auth0Exception {

        var mgmtClient = Auth0ManagementClient.forStudy(handle, studyGuid);
        Auth0Util.TestingUser testUser = Auth0Util.createTestingUser(mgmtClient);
        String auth0UserId = testUser.getAuth0Id();

        JdbiUser userDao = handle.attach(JdbiUser.class);
        JdbiClient clientDao = handle.attach(JdbiClient.class);

        Optional<Long> clientId = clientDao.getClientIdByAuth0ClientAndDomain(auth0ClientId, auth0Domain);
        if (clientId.isEmpty()) {
            throw new DDPException("No client found for " + auth0ClientId);
        }
        String userGuid = DBUtils.uniqueUserGuid(handle);
        String userHruid = DBUtils.uniqueUserHruid(handle);
        long userId = userDao.insert(auth0UserId, userGuid, clientId.get(), userHruid);

        testUser.setUserId(userId);
        testUser.setUserGuid(userGuid);
        testUser.setUserHruid(userHruid);

        mgmtClient.setUserGuidForAuth0User(auth0UserId, auth0ClientId, testUser.getUserGuid());

        AuthAPI auth = new AuthAPI(auth0Domain, auth0ClientId, auth0Secret);
        AuthRequest authRequest = auth.login(testUser.getEmail(), testUser.getPassword()).setRealm(auth0ClientName);
        TokenHolder tokenHolder = authRequest.execute();

        testUser.setToken(tokenHolder.getIdToken());
        return testUser;
    }

    public static long createCanonicalTestUser(Handle handle,
                                               long clientId,
                                               String auth0UserId,
                                               String userGuid,
                                               String userHruid) {
        return handle.attach(JdbiUser.class).insert(
                auth0UserId,
                userGuid,
                clientId,
                userHruid);
    }

    public static void deleteTestUser(
            String auth0UserId,
            String auth0Domain,
            String mgmtApiClientId,
            String mgmApiClientSecret) {
        var mgmtClient = new Auth0ManagementClient(auth0Domain, mgmtApiClientId, mgmApiClientSecret);
        var result = mgmtClient.deleteAuth0User(auth0UserId);
        if (result.hasFailure()) {
            throw new RuntimeException(result.hasThrown() ? result.getThrown() : result.getError());
        }
    }

    public static Auth0Util.TestingUser loginTestUser(Handle handle, Config auth0Config) throws Auth0Exception {
        return loginExistingTestingUser(handle,
                auth0Config.getString(AUTH0_TEST_EMAIL),
                auth0Config.getString(AUTH0_TEST_PASSWORD),
                TestConstants.TEST_USER_GUID,
                auth0Config.getString(AUTH0_CLIENT_NAME),
                auth0Config.getString(AUTH0_CLIENT_ID),
                auth0Config.getString(AUTH0_SECRET),
                TestConstants.TEST_STUDY_GUID);
    }

    public static Auth0Util.TestingUser loginTestAdminUser(Handle handle, Config auth0Config) throws Auth0Exception {
        return loginExistingTestingUser(handle,
                auth0Config.getString(AUTH0_TEST_ADMIN_EMAIL),
                auth0Config.getString(AUTH0_TEST_ADMIN_PASSWORD),
                TestConstants.TEST_ADMIN_GUID,
                auth0Config.getString(AUTH0_CLIENT_NAME),
                auth0Config.getString(AUTH0_CLIENT_ID),
                auth0Config.getString(AUTH0_SECRET),
                TestConstants.TEST_STUDY_GUID);
    }

    /**
     * Logs in an existing user, and returns information about the
     * user as well as a token that can be used for pepper routes
     */
    public static Auth0Util.TestingUser loginExistingTestingUser(Handle handle,
                                                                 String username,
                                                                 String password,
                                                                 String userGUID,
                                                                 String auth0ClientName,
                                                                 String auth0ClientId,
                                                                 String auth0Secret,
                                                                 String studyGuid)
            throws Auth0Exception {

        var mgmtClient = Auth0ManagementClient.forStudy(handle, studyGuid);
        UserDto user = handle.attach(JdbiUser.class).findByUserGuid(userGUID);
        CachedUser cachedUser = tryCachedUser(userGUID, auth0ClientId, mgmtClient.getDomain());
        if (cachedUser != null) {
            if (cachedUser.getId() == user.getUserId()) {
                LOG.info("Using cached test user");
                return cachedUser.asTestingUser();
            } else {
                LOG.warn("Cached test user id doesn't match what's in database, not using");
            }
        }

        var getResult = mgmtClient.getAuth0User(user.getAuth0UserId());
        if (getResult.hasFailure()) {
            throw new DDPException(getResult.hasThrown() ? getResult.getThrown() : getResult.getError());
        }
        User testUser = getResult.getBody();

        AuthAPI auth = new AuthAPI(mgmtClient.getDomain(), auth0ClientId, auth0Secret);
        AuthRequest authRequest = auth.login(username, password).setRealm(auth0ClientName);
        TokenHolder tokenHolder = authRequest.execute();

        cachedUser = new CachedUser(user.getUserId(), user.getUserGuid(), user.getUserHruid(), auth0ClientId,
                testUser.getId(), username, password, tokenHolder.getIdToken());
        cachedUser.write();

        return cachedUser.asTestingUser();
    }

    private static CachedUser tryCachedUser(String userGuid, String auth0ClientId, String auth0Domain) {
        CachedUser user = CachedUser.readFor(userGuid, auth0ClientId);
        if (user == null) {
            return null;
        }

        DecodedJWT jwt;
        try {
            jwt = JWTConverter.verifyDDPToken(user.getToken(), JWTConverter.defaultProvider(auth0Domain));
            if (jwt == null) {
                LOG.warn("Unable to verify or decode jwt token for cached test user");
                return null;
            }
        } catch (TokenExpiredException e) {
            LOG.warn("Cached test user token expired, not using", e);
            return null;
        } catch (Exception e) {
            LOG.warn("Error while verifying jwt token for cached test user, not using", e);
            return null;
        }

        String guidClaim = jwt.getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString();
        String clientIdClaim = jwt.getClaim(Auth0Constants.DDP_CLIENT_CLAIM).asString();
        if (!userGuid.equals(guidClaim) || !auth0ClientId.equals(clientIdClaim)) {
            LOG.warn("Cached test user token does not match expected claims, not using");
            return null;
        }

        // A minute here should be sufficient since there's also a bit of leeway allowed.
        Instant shortenedExpire = jwt.getExpiresAt().toInstant().minus(1, ChronoUnit.MINUTES);
        Instant now = Instant.now();
        if (now.equals(shortenedExpire) || now.isAfter(shortenedExpire)) {
            LOG.info("Cached test user's jwt token has or is about to expire");
            return null;
        }

        return user;
    }

    private static class CachedUser {
        @SerializedName("id")
        private long id;
        @SerializedName("guid")
        private String guid;
        @SerializedName("hruid")
        private String hruid;
        @SerializedName("auth0ClientId")
        private String auth0ClientId;
        @SerializedName("auth0Id")
        private String auth0Id;
        @SerializedName("email")
        private String email;
        @SerializedName("password")
        private String password;
        @SerializedName("token")
        private String token;

        static Path defaultCachedFilePath(String userGuid, String auth0ClientId) {
            String tmpdir = System.getProperty("ddp.cacheDir");
            if (tmpdir == null) {
                tmpdir = System.getProperty("java.io.tmpdir");
            }
            String filename = String.format("ddp-testing-cache_%s_%s.json", userGuid, auth0ClientId);
            return Paths.get(tmpdir, filename);
        }

        static CachedUser readFor(String userGuid, String auth0ClientId) {
            Gson gson = new Gson();
            Path path = defaultCachedFilePath(userGuid, auth0ClientId);

            LOG.info("Trying to read cached test user from: {}", path.toAbsolutePath());

            if (!Files.exists(path) || !Files.isReadable(path) || !Files.isRegularFile(path)) {
                LOG.warn("Cached test user file is missing or invalid, not using");
                return null;
            }

            try {
                String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                return gson.fromJson(content, CachedUser.class);
            } catch (IOException | JsonSyntaxException e) {
                LOG.warn("Encountered issues reading cached test user", e);
                return null;
            }
        }

        CachedUser(long id, String guid, String hruid, String auth0ClientId,
                   String auth0Id, String email, String password, String token) {
            this.id = id;
            this.guid = guid;
            this.hruid = hruid;
            this.auth0ClientId = auth0ClientId;
            this.auth0Id = auth0Id;
            this.email = email;
            this.password = password;
            this.token = token;
        }

        void write() {
            Gson gson = new Gson();
            Path path = defaultCachedFilePath(guid, auth0ClientId);
            String content = gson.toJson(this);

            try {
                Files.write(path, content.getBytes(StandardCharsets.UTF_8));
                LOG.info("Written cached test user to: {}", path.toAbsolutePath());
            } catch (IOException e) {
                LOG.warn("Error while writing test user", e);
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    LOG.warn("Unable to cleanup cache test user file", ex);
                }
            }
        }

        Auth0Util.TestingUser asTestingUser() {
            return new Auth0Util.TestingUser(id, guid, hruid, auth0Id, email, password, token);
        }

        public long getId() {
            return id;
        }

        public String getGuid() {
            return guid;
        }

        public String getHruid() {
            return hruid;
        }

        public String getAuth0ClientId() {
            return auth0ClientId;
        }

        public String getAuth0Id() {
            return auth0Id;
        }

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }

        public String getToken() {
            return token;
        }
    }
}
