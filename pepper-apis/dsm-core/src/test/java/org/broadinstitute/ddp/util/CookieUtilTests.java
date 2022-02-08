package org.broadinstitute.ddp.util;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.Cookie;

import org.broadinstitute.lddp.security.CookieUtil;
import org.junit.Before;
import org.junit.Test;

//todo EOB - refactor

public class CookieUtilTests {

    public static final byte[] SALT = "foo".getBytes();
    public static final String TOKEN_SECRET = "foo";
    public static final String COOKIE_NAME = "cookie1";
    public static long tokenExpirationTime = System.currentTimeMillis() + 60 * 1000;
    private String jwtToken;
    private Cookie cookie;
    private CookieUtil cookieUtil = new CookieUtil();

    @Before
    public void setUp() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "fred");
        /*claims.put(JWTUtil.KDUX_ROLES_CLAIM,Collections.singletonList("user"));
        jwtToken = new JWTUtil().createToken(TOKEN_SECRET,tokenExpirationTime,claims);
        this.cookie = getSecureCookieForToken();*/
    }

    @Test
    public void testGoodCookieAndToken() throws Exception {
        throw new Exception("TESTS NOT REFACTORED!!!!!!!");
        //boolean isValid = cookieUtil.isCookieValid(cookie.getValue(),SALT, jwtToken,TOKEN_SECRET);
        //assertTrue(isValid);
    }

    /*private Cookie getSecureCookieForToken() throws NoSuchAlgorithmException, InvalidKeySpecException {
        return new CookieUtil().createSecureCookieForToken(COOKIE_NAME,10, jwtToken, SALT);
    }

    @Test
    public void testTokenCookieMismatch() throws Exception {
        boolean isValid = cookieUtil.isCookieValid("this ain't right",SALT, jwtToken,TOKEN_SECRET);
        assertFalse(isValid);
    }

    @Test
    public void testNullCookie() throws Exception {
        boolean isValid = cookieUtil.isCookieValid(null,SALT, jwtToken,TOKEN_SECRET);
        assertFalse(isValid);
    }

    @Test
    public void testNullCookieAndNullToken() throws Exception {
        boolean isValid = cookieUtil.isCookieValid(null,SALT,null,TOKEN_SECRET);
        assertFalse(isValid);
    }*/
}
