package org.broadinstitute.ddp.db;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.MySqlTestContainerUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PooledPasswordRotationTest extends TxnAwareBaseTest  {

    private static final Logger LOG = LoggerFactory.getLogger(PooledPasswordRotationTest.class);

    private static final String BOGUS_TEST_PASSWORD = "bogus-test-password";

    private static Config originalConfig; // the config file used when the test starts.  is restored after test completes

    private static Config configWithUpdatedPassword; // the temporary config file used during this test

    private static String originalConfigFileContents;

    private static Config originalConfigPlusTestDbValues;

    private static Map<String, String> originalConfigOverrides = new HashMap<>();

    @BeforeClass
    public static void saveConfig() throws IOException  {
        ConfigManager originalConfigManager = ConfigManager.getInstance();
        originalConfigPlusTestDbValues = originalConfigManager.getConfig();
        originalConfigOverrides = originalConfigManager.getOverrides();
        originalConfig = ConfigManager.parseConfig();
        originalConfigFileContents = ConfigManager.readConfigFile();
        configWithUpdatedPassword = MySqlTestContainerUtil.overrideConfigFileDbPasswords(BOGUS_TEST_PASSWORD);
    }

    /**
     * Changes the password for the currently logged in user and
     * refreshes all pooled connections so they will use the
     * new password.
     */
    private void changePassword(Handle handle, String newPassword) {
        handle.execute("set password = password(?)", newPassword);
        handle.execute("flush privileges");

        // in the real world, pooled connections will not immediately pickup the need
        // for new credentials.  They will eventually connect with different credentials
        // based on connection expiration configuration.  Here we force the pool
        // to get into this state.
        TransactionWrapper.invalidateAllConnections();
    }

    /**
     * Verifies that password rotations can be performed by editing the conf
     * file in-place, without an app restart.
     */
    @Test
    public void testPasswordRotation() throws Exception {
        try {
            TransactionWrapper.useTxn(handle -> changePassword(handle, BOGUS_TEST_PASSWORD));
        } catch (Exception e) {
            LOG.error("Could not reset password.  Is the test database using an unusual password?", e);
        }

        // write out the original config file plus the dynamically substituted testcontainer db config values
        // otherwise, automatic reread-the-config code will fail and leave the txnwrapper in an unrecoverable state
        ConfigManager.rewriteConfigFile(originalConfigPlusTestDbValues);

        try {
            TransactionWrapper.useTxn(handle -> {
                Assert.fail("Attempt to get a connection should have failed since we changed the password");
            });
        } catch (Exception e) {
            LOG.info("Login attempt failed as expected, since we rotated the password internally but didn't reload configuration.");
            // as expected; password has been reset but the pool doesn't know it yet
        }

        // since we are dealing with a connection pool, first we should
        // reload connections so that any new connection picks up the new password
        ConfigManager.rewriteConfigFile(configWithUpdatedPassword);
        TransactionWrapper.reloadDbPoolConfiguration();

        TransactionWrapper.useTxn(handle -> {
            LOG.info("Successfully updated password");
        });
    }

    /**
     * Restores the db passwords to their original default,
     * wiping the connection pool so that subsequent tests
     * pickup the original credentials
     */
    @After
    public void restoreConfiguration() {
        try {
            String originalPassword = MySqlTestContainerUtil
                    .parseDbUrlPassword(originalConfigPlusTestDbValues.getString(ConfigFile.DB_URL))
                    .orElseThrow(() -> new DDPException("no original password in original config!"));
            TransactionWrapper.useTxn(h -> changePassword(h, originalPassword));
            // restore the original config file
            ConfigManager.rewriteConfigFile(originalConfigFileContents);
            // re-apply overrides
            ConfigManager configManager = ConfigManager.getInstance();
            for (Map.Entry<String, String> override : originalConfigOverrides.entrySet()) {
                configManager.overrideValue(override.getKey(), override.getValue());
            }
            TransactionWrapper.reloadDbPoolConfiguration();
            LOG.info("Reset test database password.");
        } catch (Exception e) {
            LOG.error("Restoring password failed.  Expect many downstream tests to fail.", e);
        }
    }
}
