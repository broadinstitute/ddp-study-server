package org.broadinstitute.ddp.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.exception.DDPException;

@Slf4j
public class DatabaseTestUtil {
    public static final String TESTING_ROOT_USERNAME = "root";
    public static final String TEST = "test";
    public static final String MYSQL_VERSION = "mysql:5.7";

    // classpath location for init script for disposable mysql test db
    public static final String TEST_DB_MYSQL_CONF_DIR = "testdbs";

    private DatabaseTestUtil() {}

    /**
     * Set up a database for testing based on supplied configuration and initialize TransactionWrapper
     */
    public static void initDbConnection(Config cfg, TransactionWrapper.DB db) {
        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String dbUrl = cfg.getString(db.getDbUrlConfigKey());
        log.info("Initializing db pool");
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(db, maxConnections, dbUrl));

        LiquibaseUtil.runLiquibase(dbUrl, db, LiquibaseUtil.LIQUIBASE_TEST_CONTEXT);
    }

    public static void closeDbConnection() {
        TransactionWrapper.reset();
    }

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
            log.error("invalid db url", e);
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
                    throw new DDPException("Could not find old password in config file for database URL key");
                }
            } else {
                throw new DDPException("Database URL key does not exist in config file");
            }
        }
        return modifiedConfig;
    }

    /**
     * Overwrites the given key/value located by using
     * {@link ConfigManager#overrideValue(String, String)}
     */
    public static void rewriteConfigFileDbUrl(String dbUrlKey, String dbUrl) throws RuntimeException {
        try {
            ConfigManager.getInstance().overrideValue(dbUrlKey, DatabaseTestUtil.getFullJdbcTestUrl(dbUrl));
        } catch (Exception e) {
            throw new RuntimeException("Could not rewrite database URL", e);
        }
    }

    public static String getFullJdbcTestUrl(String jdbcUrl) {
        return jdbcUrl  + "?user=" + TESTING_ROOT_USERNAME
                + "&password=" + TEST
                + "&characterEncoding=UTF-8&useLegacyDatetimeCode=false&serverTimezone=UTC"
                + "&useSSL=false&sessionVariables=innodb_strict_mode=on,tx_isolation='READ-COMMITTED',"
                + "sql_mode='TRADITIONAL'";
    }
}
