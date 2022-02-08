package org.broadinstitute.ddp.jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//todo EOB - revisit tests
public class JWTUtilTest {

    private static final Logger logger = LoggerFactory.getLogger(JWTUtilTest.class);
    private static final String QUARTERBACK = "Quarterback";
    private static final String MOVIE_STAR = "Movie Star";
    String secret = "not a great secret here";
    String user = "Tom Brady";
    Collection<String> roles = Arrays.asList(MOVIE_STAR, QUARTERBACK);
    String jwtToken;
    private long tokenDurationInMilliseconds = 5 * 1000; //
    long invalidAfter = (System.currentTimeMillis() + tokenDurationInMilliseconds) / 1000;

    @Before
    public void createToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user", user);
        /*claims.put(JWTUtil.KDUX_ROLES_CLAIM,roles);
        jwtToken = jwtUtil.createToken(secret,invalidAfter,claims);*/
    }

    @Test
    public void testGoodSecretWorks() throws Exception {
        throw new Exception("TESTS NOT REFACTORED!!!!!!!");
        //assertTrue("Token should be valid",jwtUtil.isTokenValid(secret,jwtToken));
    }

    /*@Test
    @Category(FastTests.class)
    public void testRolesClaim() throws Exception {
        Map<String,Object> claims = jwtUtil.verifyAndGetClaims(secret,jwtToken);
        Collection<String> roles = (Collection<String>)claims.get(JWTUtil.KDUX_ROLES_CLAIM);
        assertEquals("Should have two roles in the token",2,roles.size());
        assertTrue(roles.contains(QUARTERBACK));
        assertTrue(roles.contains(MOVIE_STAR));
    }

    @Test
    @Category(FastTests.class)
    public void testBadSecretFails() {
        assertFalse("Token should not be valid",jwtUtil.isTokenValid("not the right secret",jwtToken));
    }

    @Category(SlowTests.class)
    @Test
    public void testTokenExpiration() {
        logger.info("This test may take a while, as it sleeps for a bit over " + (tokenDurationInMilliseconds/1000) + " seconds until the
         token expires.");
        try {
            Thread.sleep(tokenDurationInMilliseconds + 1000);
            Assert.assertFalse("Token is expired, should not be considered valid",jwtUtil.isTokenValid(secret,jwtToken));
        }
        catch(InterruptedException e) {
            Assert.fail("Sleep interrupted, cannot wait for token to expire.");
        }
    }*/
}
