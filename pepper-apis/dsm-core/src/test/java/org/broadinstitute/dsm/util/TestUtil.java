package org.broadinstitute.dsm.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Cookie;

import com.google.gson.GsonBuilder;
import com.typesafe.config.Config;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.broadinstitute.lddp.security.CookieUtil;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtil {

    public static final String UNIT_TEST = "UNIT_TEST";
    private static final Logger logger = LoggerFactory.getLogger(TestUtil.class);
    private String jwtToken;
    private Cookie csrfCookie;
    private String cookieName;

    public TestUtil(String jwtToken, Cookie csrfCookie, String cookieName) {
        this.jwtToken = jwtToken;
        this.csrfCookie = csrfCookie;
        this.cookieName = cookieName;
    }

    public static TestUtil newInstance(Config cfg) throws Exception {
        String jwtSecret = cfg.getString("browser_security.jwt_secret");
        String cookieSalt = cfg.getString("browser_security.cookie_salt");
        String cookieName = cfg.getString("browser_security.cookie_name");

        Map<String, String> claims = new HashMap<>();
        claims.put("USER_ID", "26");
        String jwtToken = new SecurityHelper().createToken(jwtSecret, (System.currentTimeMillis() / 1000) + (60 * 18), claims);

        CookieUtil cookieUtil = new CookieUtil();
        int cookieAgeInSeconds = 60;
        Cookie csrfCookie = cookieUtil.createSecureCookieForToken(cookieName, cookieAgeInSeconds, jwtToken, cookieSalt.getBytes());
        return new TestUtil(jwtToken, csrfCookie, cookieName);
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
        authHeaders.put("Cookie", cookieName + "=" + csrfCookie.getValue() + ";");
        authHeaders.put("Authorization", "Bearer " + jwtToken);
        return authHeaders;
    }

    public Map<String, String> buildHeaders(String secret) {
        int cookieAgeInSeconds = 60;
        Map<String, String> claims = new HashMap<>();
        String jwtToken = new SecurityHelper().createToken(secret, cookieAgeInSeconds + (System.currentTimeMillis() / 1000) + (60 * 5),
                claims);

        Map<String, String> authHeaders = new HashMap<>();
        authHeaders.put("Authorization", "Bearer " + jwtToken);
        return authHeaders;
    }
}
