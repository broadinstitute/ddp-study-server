package org.broadinstitute.lddp.security;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.servlet.http.Cookie;

/**
 * To avoid the problem of token theft, whereby a nefarious user could
 * copy their token out of the browser and then do whatever they
 * want against the server side outside of the browser, whenever we hand out a
 * jwt token, we also use a secure, http only cookie, whose
 * value is a hash of the salted jwt token.

 * <p>On every route other than the auth route, we check to ensure that
 * the cookie matches the jwt token.</p>
 */
public class CookieUtil {

    public Cookie createSecureCookieForToken(String cookieName, int cookieAgeInSeconds, String token, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        Cookie cookie = new Cookie(cookieName, hashToken(token, salt));
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        cookie.setMaxAge(cookieAgeInSeconds);
        return cookie;
    }

    private String hashToken(String token, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(token.toCharArray(), salt, 65536, 256);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        byte[] hash = f.generateSecret(spec).getEncoded();
        return new String(Base64.getEncoder().encode(hash));
    }

    public boolean isCookieValid(String cookieValue, byte[] cookieSalt, String jwtToken, String tokenSecret)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        boolean isValidToken = SecurityHelper.validToken(tokenSecret, jwtToken);
        boolean doesCookieMatchToken = false;

        if (isValidToken) {
            doesCookieMatchToken = hashToken(jwtToken, cookieSalt).equals(cookieValue);
            if (!doesCookieMatchToken) {
                // todo arz issue alert to monitoring--this smells like token theft
            }
        }
        return doesCookieMatchToken;
    }
}
