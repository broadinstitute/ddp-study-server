package org.broadinstitute.ddp.util;

import java.util.concurrent.TimeUnit;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.testcontainers.containers.MySQLContainer;

@Slf4j
public class DBTestContainer {
    private static MySQLContainer apisDb;

    private static MySQLContainer housekeepingDb;

    private static boolean hasInitialized = false;

    public static synchronized void initializeTestDbs() {
        if (hasInitialized) {
            log.info("Already initialized test dbs");
            return;
        }
        Config cfg = ConfigManager.getInstance().getConfig();
        Boolean useDisposableTestDbs = ConfigUtil.getBoolIfPresent(cfg, ConfigFile.USE_DISPOSABLE_TEST_DB);
        if (useDisposableTestDbs != null && !useDisposableTestDbs) {
            log.warn("Disposable test dbs will not be used. Real database connection urls should be provided in configuration file.");
            hasInitialized = true;
            return;
        }

        log.info("Starting test dbs.  This may take a while due to docker image fetchery");

        long containerStartTime = System.currentTimeMillis();
        apisDb = new MySQLContainer(DatabaseTestUtil.MYSQL_VERSION);
        housekeepingDb = new MySQLContainer(DatabaseTestUtil.MYSQL_VERSION);
        housekeepingDb.withConfigurationOverride(DatabaseTestUtil.TEST_DB_MYSQL_CONF_DIR);
        apisDb.withConfigurationOverride(DatabaseTestUtil.TEST_DB_MYSQL_CONF_DIR);

        apisDb.start();
        housekeepingDb.start();
        log.info("It took {}ms to start the test containers.", System.currentTimeMillis() - containerStartTime);

        DatabaseTestUtil.rewriteConfigFileDbUrl(ConfigFile.DB_URL, apisDb.getJdbcUrl());
        DatabaseTestUtil.rewriteConfigFileDbUrl(ConfigFile.HOUSEKEEPING_DB_URL, housekeepingDb.getJdbcUrl());

        Runnable containerShutdown = () -> {
            log.info("Shutting down test databases");
            if (apisDb != null) {
                try {
                    apisDb.stop();
                } catch (Exception e) {
                    log.error("Trouble shutting down disposable apis db", e);
                }
            }
            if (housekeepingDb != null) {
                try {
                    housekeepingDb.stop();
                } catch (Exception e) {
                    log.error("Trouble shutting down disposable housekeeping db", e);
                }
            }

        };
        Runtime.getRuntime().addShutdownHook(new Thread(containerShutdown));

        try {
            log.info("Pausing for {}ms for test dbs to stabilize", 2000);
            TimeUnit.MILLISECONDS.sleep(2000);
        } catch (InterruptedException e) {
            log.info("Wait interrupted", e);
        }
        hasInitialized = true;
    }
}
