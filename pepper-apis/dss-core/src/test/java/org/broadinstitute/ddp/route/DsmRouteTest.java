package org.broadinstitute.ddp.route;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import com.auth0.client.auth.AuthAPI;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.net.AuthRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.security.JWTConverter;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil.GeneratedTestData;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Handles additional setup required for DSM client based integration tests
 * Auth for DSM is slightly different than the other auth0 methods. This creates the DSM
 * client entry in the database, then logs in the DSM client. Additionally calls
 * {@link TestDataSetupUtil} to create a user/profile/study and mail address that
 * is all internally consistent
 */
@Slf4j
public class DsmRouteTest extends IntegrationTestSuite.TestCase {
    protected static GeneratedTestData generatedTestData;
    protected static String userGuid;
    protected static String studyGuid;
    protected static String dsmClientAccessToken;
    protected static String auth0ClientId;
    protected static long auth0TenantId;

    // If DSM client already exists in database, then re-use it.
    // Otherwise, insert it and clean it up afterwards.
    private static boolean insertedDsmClient = false;

    @BeforeClass
    public static void userSetup() {
        TransactionWrapper.useTxn(handle -> {
            setupTestDSMAuthClientInDatabase(handle);

            generatedTestData = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = generatedTestData.getTestingUser().getUserGuid();
            studyGuid = generatedTestData.getStudyGuid();

            TestDataSetupUtil.createTestingMailAddress(handle, generatedTestData);
        });

        if (dsmClientAccessToken == null) {
            try {
                dsmClientAccessToken = loginDsmTestUser();
            } catch (Auth0Exception e) {
                throw new RuntimeException("Couldn't login DSM Client during integration tests", e);
            }
        }
    }

    @AfterClass
    public static void userCleanup() {
        TransactionWrapper.useTxn(handle -> {
            if (insertedDsmClient) {
                deleteTestDSMAuthClientInDatabase(handle);
            }
        });
    }

    /**
     * Adds the DSM Client entry to the database so we can log in
     *
     * @param handle transaction handle
     */
    private static void setupTestDSMAuthClientInDatabase(Handle handle) {
        ClientDao clientDao = handle.attach(ClientDao.class);
        Config auth0Config = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0);

        String auth0Domain = auth0Config.getString(ConfigFile.DSM_DOMAIN);
        auth0TenantId = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain).getId();
        auth0ClientId = auth0Config.getString(ConfigFile.AUTH0_DSM_CLIENT_ID);

        StudyClientConfiguration clientConfig = clientDao.getConfiguration(auth0ClientId, auth0Domain);
        if (clientConfig == null) {
            clientDao.registerClient(auth0ClientId,
                    auth0Config.getString(ConfigFile.AUTH0_DSM_CLIENT_SECRET),
                    new ArrayList<>(),
                    auth0Config.getString(ConfigFile.ENCRYPTION_SECRET),
                    auth0TenantId);
            insertedDsmClient = true;
        }
    }

    /**
     * Removes DSM Client entry from database
     *
     * @param handle transaction handle
     */
    private static void deleteTestDSMAuthClientInDatabase(Handle handle) {
        ClientDao clientDao = handle.attach(ClientDao.class);
        clientDao.deleteByAuth0ClientIdAndAuth0TenantId(auth0ClientId, auth0TenantId);
    }

    /**
     * Get an access token for a test DSM client, using a cached token if it exists.
     * Note that we use a a test DSM client secret but not a test DSM client id
     * For integration testing the DataDonationPlatform instance will read the "real" DSM client id
     * The client secret has to match the "real" DSM client or authentication of token will not work.
     *
     * @return the jwt access token
     * @throws Auth0Exception if something went wrong
     */
    public static String loginDsmTestUser() throws Auth0Exception {
        Config auth0Config = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0);

        String authDomain = auth0Config.getString(ConfigFile.DSM_DOMAIN);
        String dsmClientId = auth0Config.getString(ConfigFile.AUTH0_DSM_CLIENT_ID);
        String dsmClientSecret = auth0Config.getString(ConfigFile.AUTH0_DSM_CLIENT_SECRET);
        String dsmApiAudience = auth0Config.getString(ConfigFile.AUTH0_DSM_API_AUDIENCE);

        CachedDsmCredentials creds = tryCachedDsmCredentials(authDomain, dsmClientId);
        if (creds != null) {
            log.info("Using cached dsm token");
            return creds.getToken();
        }

        AuthAPI auth = new AuthAPI(authDomain,
                dsmClientId,
                dsmClientSecret);

        AuthRequest request = auth.requestToken(dsmApiAudience);
        TokenHolder holder = request.execute();

        creds = new CachedDsmCredentials(authDomain, dsmClientId, holder.getAccessToken());
        creds.write();

        return creds.getToken();
    }

    private static CachedDsmCredentials tryCachedDsmCredentials(String auth0Domain, String auth0ClientId) {
        CachedDsmCredentials creds = CachedDsmCredentials.readFor(auth0ClientId);
        if (creds == null) {
            return null;
        }

        DecodedJWT jwt;
        try {
            jwt = JWTConverter.verifyDDPToken(creds.getToken(), JWTConverter.defaultProvider(auth0Domain));
            if (jwt == null) {
                log.warn("Unable to verify or decode jwt token for cached dsm credentials");
                return null;
            }
        } catch (TokenExpiredException e) {
            log.warn("Cached dsm token expired, not using", e);
            return null;
        } catch (Exception e) {
            log.warn("Error while verifying cached dsm jwt token, not using", e);
            return null;
        }

        if (!auth0Domain.equals(creds.getDomain()) || !auth0ClientId.equals(creds.getClientId())) {
            log.warn("Cached dsm credentials does not match expected values, not using");
            return null;
        }

        Instant shortenedExpire = jwt.getExpiresAt().toInstant().minus(1, ChronoUnit.MINUTES);
        Instant now = Instant.now();
        if (now.equals(shortenedExpire) || now.isAfter(shortenedExpire)) {
            log.info("Cached dsm credentials's jwt token has or is about to expire");
            return null;
        }

        return creds;
    }

    private static class CachedDsmCredentials {

        @SerializedName("domain")
        private String domain;

        @SerializedName("clientId")
        private String clientId;

        @SerializedName("token")
        private String token;

        static Path defaultCachedFilePath(String auth0ClientId) {
            String tmpdir = System.getProperty("ddp.cacheDir");
            if (tmpdir == null) {
                tmpdir = System.getProperty("java.io.tmpdir");
            }
            String filename = String.format("ddp-testing-cache_dsm_%s.json", auth0ClientId);
            return Paths.get(tmpdir, filename);
        }

        /**
         * Read and deserialize the cached dsm credentials from default path in filesystem, if it exists.
         *
         * @param auth0ClientId the dsm client id to look for
         * @return dsm credentials object, or null if not found
         */
        static CachedDsmCredentials readFor(String auth0ClientId) {
            Gson gson = new Gson();
            Path path = defaultCachedFilePath(auth0ClientId);

            log.info("Trying to read cached dsm credentials from: {}", path.toAbsolutePath());

            if (!Files.exists(path) || !Files.isReadable(path) || !Files.isRegularFile(path)) {
                log.warn("Cached dsm credentials file is missing or invalid, not using");
                return null;
            }

            try {
                var content = Files.readString(path);
                return gson.fromJson(content, CachedDsmCredentials.class);
            } catch (IOException | JsonSyntaxException e) {
                log.warn("Encountered issues reading cached dsm credentials", e);
                return null;
            }
        }

        CachedDsmCredentials(String domain, String clientId, String token) {
            this.domain = domain;
            this.clientId = clientId;
            this.token = token;
        }

        /**
         * Serialize and writes the dsm credentials to default path in filesystem so it's cached for later use.
         */
        void write() {
            Gson gson = new Gson();
            Path path = defaultCachedFilePath(clientId);
            String content = gson.toJson(this);

            try {
                Files.write(path, content.getBytes(StandardCharsets.UTF_8));
                log.info("Written cached dsm credentials to: {}", path.toAbsolutePath());
            } catch (IOException e) {
                log.warn("Error while writing dsm credentials", e);
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("Unable to cleanup cache dsm credentials file", ex);
                }
            }
        }

        public String getDomain() {
            return domain;
        }

        public String getClientId() {
            return clientId;
        }

        public String getToken() {
            return token;
        }
    }
}
