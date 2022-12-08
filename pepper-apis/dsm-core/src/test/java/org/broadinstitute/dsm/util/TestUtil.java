package org.broadinstitute.dsm.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.security.RSAKeyProviderFactory;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.lddp.security.Auth0Util;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtil {

    public static final String UNIT_TEST = "UNIT_TEST";
    private static final Logger logger = LoggerFactory.getLogger(TestUtil.class);
    private static Config config;
    private String jwtToken;
    private String cookieName;

    public TestUtil(String jwtToken, String cookieName) {
        this.jwtToken = jwtToken;
        this.cookieName = cookieName;
    }

    public static TestUtil newInstance(Config cfg) throws Exception {
        String jwtSecret = cfg.getString("browser_security.jwt_secret");
        String cookieName = cfg.getString("browser_security.cookie_name");

        Map<String, String> claims = new HashMap<>();
        config = cfg;
//        try {
//            var algorithm = Algorithm.HMAC256(jwtSecret);
//            long expiresAt = Instant.now().plus(1, ChronoUnit.MINUTES).toEpochMilli();
//            JWTCreator.Builder builder = JWT.create();
//            builder.withIssuer(null);
//            builder.withExpiresAt(new Date(expiresAt));
//            String jwtToken = builder.sign(algorithm);
//            return new TestUtil(jwtToken, cookieName);
//        } catch (UnsupportedEncodingException e) {
//            throw new IllegalArgumentException("Invalid DSM JWT secret", e);
//        }
        // String jwtToken = new SecurityHelper().createToken(jwtSecret, (System.currentTimeMillis() / 1000) + (60 * 18), claims);
        return new TestUtil(createToken("26"), cookieName);

    }

    public static String createToken(String userId) {
        // return DSMServer.getAuth0Util().getAccessToken();
        Auth0Util auth0Util = new Auth0Util(config.getString(ApplicationConfigConstants.AUTH0_ACCOUNT),
                config.getStringList(ApplicationConfigConstants.AUTH0_CONNECTIONS),
                config.getString(ApplicationConfigConstants.AUTH0_CLIENT_KEY), config.getString(ApplicationConfigConstants.AUTH0_SECRET),
                config.getString(ApplicationConfigConstants.AUTH0_MGT_KEY), config.getString(ApplicationConfigConstants.AUTH0_MGT_SECRET),
                config.getString(ApplicationConfigConstants.AUTH0_MGT_API_URL),
                config.getString(ApplicationConfigConstants.AUTH0_AUDIENCE));
        return auth0Util.requestAuth0Token(config.getString(ApplicationConfigConstants.AUTH0_DOMAIN),
                config.getString(ApplicationConfigConstants.AUTH0_CLAIM_NAMESPACE), Map.of("USER_ID", userId + ""));
               // cfg.getString(ApplicationConfigConstants.AUTH0_CLAIM_NAMESPACE), new HashMap<>());

//        RSAKeyProvider keyProvider = null;
//        try {
//            JwkProvider jwkProvider = new JwkProviderBuilder(cfg.getString("auth0.domain")).build();
//            keyProvider = RSAKeyProviderFactory.createRSAKeyProviderWithPrivateKeyOnly(jwkProvider);
//            Algorithm algorithm = Algorithm.RSA256(keyProvider);
//            try {
//                JWTCreator.Builder builder = com.auth0.jwt.JWT.create();
//                return builder.sign(algorithm);
//            } catch (Exception e) {
//                throw new RuntimeException("Couldn't create token " + e);
//            }
//        } catch (Exception e) {
//            Assert.fail();
//        }
//        return null;
    }

    public static File getResouresFile(String name) {
        ClassLoader classLoader = TestUtil.class.getClassLoader();
        return new File(classLoader.getResource(name).getFile());
    }

    public static String readFile(String name) throws Exception {
        ClassLoader classLoader = TestUtil.class.getClassLoader();
        File file = new File(classLoader.getResource(name).getFile());
        BufferedReader rd = new BufferedReader(new FileReader(file));
        return org.apache.commons.io.IOUtils.toString(rd);
    }

    public static Response performGet(String baseAppUrl, String path, Map<String, String> headers) throws IOException {
        Request request = Request.Get(baseAppUrl + path);
        if (headers != null) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }
        return request.execute();
    }

    public static Response perform(Request request, Object objectToPost, Map<String, String> headers) throws IOException {
        if (headers != null) {
            for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
                request = request.addHeader(headerEntry.getKey(), headerEntry.getValue());
            }
        }

        if (objectToPost != null) {
            String content = null;
            if (!(objectToPost instanceof String)) {
                content = new GsonBuilder().serializeNulls().create().toJson(objectToPost);
            } else {
                content = (String) objectToPost;
            }
            request.bodyString(content, ContentType.APPLICATION_JSON);
        }
        return request.execute();
    }

    public static void generatePDF(InputStream inputStream, String folder, String file) {
        OutputStream outputStream = null;
        try {
            File destFile = new File(folder, file);
            if (!destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }
            destFile.createNewFile();
            outputStream = new FileOutputStream(destFile);

            int read = 0;
            byte[] bytes = new byte[1024];

            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        } catch (IOException e) {
            logger.error("Failed to generate PDF " + file + " at directory " + folder, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.error("Failed to generate PDF " + file + " at directory " + folder, e);
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    logger.error("Failed to generate PDF " + file + " at directory " + folder, e);
                }
            }
        }
    }

    public Map<String, String> buildAuthHeaders() {
        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + jwtToken);
        return authHeaders;
    }

    public Map<String, String> buildAuthHeaders(String userId) {
        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + createToken(userId));
        return authHeaders;
    }

    public Map<String, String> buildHeaders(String secret) {
        int cookieAgeInSeconds = 60;
        Map<String, String> claims = new HashMap<>();
        String jwtToken =
                new SecurityHelper().createToken(secret, cookieAgeInSeconds + (System.currentTimeMillis() / 1000) + (60 * 5), claims);

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + jwtToken);
        return authHeaders;
    }
}
