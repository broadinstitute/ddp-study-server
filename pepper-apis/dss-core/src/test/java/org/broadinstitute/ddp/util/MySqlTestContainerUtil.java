package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

public class MySqlTestContainerUtil {

    private static final Logger LOG = LoggerFactory.getLogger(MySqlTestContainerUtil.class);

    public static final String TESTING_ROOT_USERNAME = "root";
    public static final String TESTING_ROOT_PASSWORD = "test";

    public static final String MYSQL_VERSION = "mysql:5.7";

    // classpath location for init script for disposable mysql test db
    public static final String TEST_DB_MYSQL_CONF_DIR = "testdbs";

    static MySQLContainer apisDb;

    static MySQLContainer housekeepingDb;

    private static boolean hasInitialized = false;

    public static Optional<String> parseDbUrlPassword(String dbUrl) {
        try {
            URI url = new URI(dbUrl.replace("jdbc:", ""));
            String[] params = url.getQuery().split("&");
            for (String param : params) {
                String[] parts = param.split("=");
                if ("password".equals(parts[0])) {
                    return Optional.of(parts[1]);
                }
            }
        } catch (URISyntaxException e) {
            LOG.error("invalid db url", e);
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Creates a new Config by overwriting the database passwords using
     * the given values
     */
    public static Config overrideConfigFileDbPasswords(String newPassword) {
        Config modifiedConfig = ConfigManager.getInstance().getConfig();
        String[] dbUrlKeys = new String[] {ConfigFile.DB_URL, ConfigFile.HOUSEKEEPING_DB_URL};
        for (String dbUrlKey : dbUrlKeys) {
            if (modifiedConfig.hasPath(dbUrlKey)) {
                String dbUrlValue = modifiedConfig.getString(dbUrlKey);
                String oldPassword = parseDbUrlPassword(dbUrlValue).orElse(null);
                if (oldPassword != null) {
                    String newDbUrl = dbUrlValue.replace("&password=" + oldPassword, "&password=" + newPassword);
                    modifiedConfig = modifiedConfig.withValue(dbUrlKey, ConfigValueFactory.fromAnyRef(newDbUrl, "Overridden value"));
                } else {
                    throw new DDPException("Could not find old password in config file for db url key: " + dbUrlKey);
                }
            } else {
                throw new DDPException("Key " + dbUrlKey + " does not exist in config file");
            }
        }
        return modifiedConfig;
    }

    /**
     * Overwrites the given key/value located by using
     * {@link ConfigManager#overrideValue(String, String)}
     */
    public static void rewriteConfigFileDbUrl(String dbUrlKey, String newDbUrl) throws IOException {
        ConfigManager.getInstance().overrideValue(dbUrlKey, newDbUrl);
    }

    public static synchronized void initializeTestDbs() {
        if (!hasInitialized) {
            Config cfg = ConfigManager.getInstance().getConfig();
            Boolean useDisposableTestDbs = ConfigUtil.getBoolIfPresent(cfg, ConfigFile.USE_DISPOSABLE_TEST_DB);
            if (useDisposableTestDbs != null && !useDisposableTestDbs) {
                LOG.warn("Disposable test dbs will not be used. Real database connection urls should be provided in configuration file.");
                hasInitialized = true;
                return;
            }

            LOG.info("Starting test dbs.  This may take a while due to docker image fetchery");

            long containerStartTime = System.currentTimeMillis();
            apisDb = new MySQLContainer(MYSQL_VERSION);
            housekeepingDb = new MySQLContainer(MYSQL_VERSION);
            housekeepingDb.withConfigurationOverride(TEST_DB_MYSQL_CONF_DIR);
            apisDb.withConfigurationOverride(TEST_DB_MYSQL_CONF_DIR);

            apisDb.start();
            housekeepingDb.start();

            LOG.info("It took {}ms to start the test containers.", System.currentTimeMillis() - containerStartTime);

            String apisUrl = getFullJdbcTestUrl(apisDb);
            String housekeepingUrl = getFullJdbcTestUrl(housekeepingDb);

            try {
                rewriteConfigFileDbUrl(ConfigFile.DB_URL, apisUrl);
            } catch (IOException e) {
                throw new RuntimeException("Could not rewrite " + ConfigFile.DB_URL, e);
            }

            try {
                rewriteConfigFileDbUrl(ConfigFile.HOUSEKEEPING_DB_URL, housekeepingUrl);
            } catch (IOException e) {
                throw new RuntimeException("Could not rewrite " + ConfigFile.HOUSEKEEPING_DB_URL, e);
            }

            Runnable containerShutdown = () -> {
                LOG.info("Shutting down test databases");
                if (apisDb != null) {
                    try {
                        apisDb.stop();
                    } catch (Exception e) {
                        LOG.error("Trouble shutting down disposable apis db", e);
                    }
                }
                if (housekeepingDb != null) {
                    try {
                        housekeepingDb.stop();
                    } catch (Exception e) {
                        LOG.error("Trouble shutting down disposable housekeeping db", e);
                    }
                }

            };
            Runtime.getRuntime().addShutdownHook(new Thread(containerShutdown));

            try {
                LOG.info("Pausing for {}ms for test dbs to stabilize", 2000);
                TimeUnit.MILLISECONDS.sleep(2000);
            } catch (InterruptedException e) {
                LOG.info("Wait interrupted", e);
            }
            hasInitialized = true;
        } else {
            LOG.info("Already initialized test dbs");
        }
    }

    public static String getFullJdbcTestUrl(MySQLContainer testDb) {
        return testDb.getJdbcUrl()  + "?user=" + TESTING_ROOT_USERNAME
                + "&password=" + TESTING_ROOT_PASSWORD
                + "&characterEncoding=UTF-8&useLegacyDatetimeCode=false&serverTimezone=UTC"
                + "&useSSL=false&sessionVariables=innodb_strict_mode=on,tx_isolation='READ-COMMITTED',"
                + "sql_mode='TRADITIONAL'";
    }
}
