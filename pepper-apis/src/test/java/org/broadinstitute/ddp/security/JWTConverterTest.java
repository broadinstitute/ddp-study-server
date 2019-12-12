package org.broadinstitute.ddp.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDao;
import org.broadinstitute.ddp.db.UserDaoFactory;
import org.broadinstitute.ddp.route.IntegrationTestSuite;
import org.broadinstitute.ddp.route.RouteTestUtil;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class JWTConverterTest extends IntegrationTestSuite.TestCase {

    private static Config auth0Config;
    private static UserDao userDao;
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String token;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
        token = testData.getTestingUser().getToken();
        auth0Config = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0);
        userDao = UserDaoFactory.createFromSqlConfig(RouteTestUtil.getSqlConfig());
    }

    @Test
    public void testUserGuidIsInAuth0Token() {
        DecodedJWT unverifiedToken = JWT.decode(token);
        String ddpUserId = unverifiedToken.getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString();
        assertEquals(testData.getUserGuid(), ddpUserId);
    }

    @Test
    public void testConvertJWTFromHeader() {
        String expectedUserGuid = testData.getUserGuid();
        String expectedClient = testData.getTestingClient().getAuth0ClientId();

        JWTConverter jwtConverter = new JWTConverter(userDao);
        DDPAuth ddpAuth = jwtConverter.convertJWTFromHeader("Bearer " + token);

        assertTrue(ddpAuth.isActive());
        assertEquals(expectedClient, ddpAuth.getClient());
        assertEquals(expectedUserGuid, ddpAuth.getOperator());
    }
}
