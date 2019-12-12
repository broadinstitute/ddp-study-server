package org.broadinstitute.ddp.util;

import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;

import com.auth0.exception.Auth0Exception;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ConfigFile.Auth0Testing;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.security.JWTConverter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestingUserUtilTest extends TxnAwareBaseTest {

    private static Auth0Util.TestingUser testingUser;

    private static Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
    private static final Logger LOG = LoggerFactory.getLogger(TestingUserUtilTest.class);

    private static String mgmtApiClientId = auth0Config.getString(Auth0Testing.AUTH0_MGMT_API_CLIENT_ID);
    private static String mgmtApiSecret = auth0Config.getString(Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET);
    private static String auth0TestClientId = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_ID);
    private static String testClientName = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_CLIENT_NAME);
    private static String testClientSecret = auth0Config.getString(ConfigFile.BACKEND_AUTH0_TEST_SECRET);
    private static String domain = auth0Config.getString(ConfigFile.DOMAIN);
    private static String encryptionSecret = auth0Config.getString(ConfigFile.ENCRYPTION_SECRET);

    private static StudyDto studyDto;

    @BeforeClass
    public static void setupTest() {
        TransactionWrapper.useTxn(handle ->
                studyDto = TestDataSetupUtil.generateTestStudy(handle, domain, mgmtApiClientId, mgmtApiSecret));
    }

    @AfterClass
    public static void deleteTestingUser() throws Auth0Exception {
        if (testingUser != null) {
            if (testingUser.getAuth0Id() != null) {
                TestingUserUtil.deleteTestUser(testingUser.getAuth0Id(),
                                               domain,
                                               mgmtApiClientId,
                                               mgmtApiSecret);
            }
        } else {
            LOG.error("No testingUser initialized; nothing to delete");
        }
    }

    /**
     * Verifies that the generated test user has the necessary bits
     * to be useful: valid token with DDP claims that match what
     * was generated
     */
    @Test
    public void testCreateAndLoginNewTestUser() throws Exception {
        TestingUserUtil testingUserUtil = new TestingUserUtil();

        testingUser = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
            JdbiClient clientDao = handle.attach(JdbiClient.class);
            long auth0TenantId = handle.attach(JdbiAuth0Tenant.class).findByDomain(domain).getId();
            if (!clientDao.getClientIdByAuth0ClientAndDomain(auth0TestClientId, domain).isPresent()) {
                handle.attach(ClientDao.class)
                        .registerClient(testClientName, auth0TestClientId, testClientSecret, new ArrayList<>(),
                                encryptionSecret, auth0TenantId);
            }
            return testingUserUtil.createAndLoginNewTestUser(handle, domain, testClientName, auth0TestClientId,
                    testClientSecret, studyDto.getGuid());
        });

        assertThat(testingUser.getToken(), not(isEmptyOrNullString()));

        DecodedJWT verifiedJWT = JWTConverter.verifyDDPToken(testingUser.getToken(),
                new JwkProviderBuilder(domain).build());
        assertThat(verifiedJWT.getSubject(), equalTo(testingUser.getAuth0Id()));
        assertThat(verifiedJWT.getClaim(Auth0Constants.DDP_USER_ID_CLAIM).asString(), equalTo(testingUser.getUserGuid()));
        assertThat(verifiedJWT.getClaim(Auth0Constants.DDP_CLIENT_CLAIM).asString(), equalTo(auth0TestClientId));
    }
}
