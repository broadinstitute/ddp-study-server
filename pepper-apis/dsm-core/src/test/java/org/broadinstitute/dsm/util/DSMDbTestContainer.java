package org.broadinstitute.dsm.util;

import java.util.concurrent.TimeUnit;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.DatabaseTestUtil;
import org.testcontainers.containers.MySQLContainer;

@Slf4j
public class DSMDbTestContainer {
    private static MySQLContainer dsmDb;
    private static boolean hasInitialized = false;

    public static synchronized void initializeTestDbs() {
        if (hasInitialized) {
            log.info("Already initialized DSM test db");
            return;
        }
        Config cfg = ConfigManager.getInstance().getConfig();
        Boolean useDisposableTestDbs = ConfigUtil.getBoolIfPresent(cfg, ConfigFile.USE_DISPOSABLE_TEST_DB);
        if (useDisposableTestDbs != null && !useDisposableTestDbs) {
            log.warn("Disposable test dbs will not be used. Real database connection urls should be provided in configuration file.");
            hasInitialized = true;
            return;
        }

        log.info("Starting DSM test db container");

        long containerStartTime = System.currentTimeMillis();
        dsmDb = new MySQLContainer(DatabaseTestUtil.MYSQL_VERSION);
        dsmDb.withConfigurationOverride(DatabaseTestUtil.TEST_DB_MYSQL_CONF_DIR);

        dsmDb.start();
        log.info("It took {}ms to start DSM DB test container", System.currentTimeMillis() - containerStartTime);

        DatabaseTestUtil.rewriteConfigFileDbUrl(ConfigFile.DB_URL, dsmDb.getJdbcUrl());

        Runnable containerShutdown = () -> {
            log.info("Shutting down DSM test database container");
            if (dsmDb != null) {
                try {
                    dsmDb.stop();
                } catch (Exception e) {
                    log.error("Trouble shutting down DSM test database container", e);
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(containerShutdown));

        try {
            log.info("Pausing for {}ms for DSM test db container to stabilize", 2000);
            TimeUnit.MILLISECONDS.sleep(2000);
        } catch (InterruptedException e) {
            log.info("Wait interrupted", e);
        }
        hasInitialized = true;
    }
}
