package org.broadinstitute.ddp;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.util.ConfigManager;
import org.junit.BeforeClass;

/**
 * Basic setup for getting configuration data into a test.
 */
public abstract class ConfigAwareBaseTest {

    protected static Config sqlConfig;
    protected static Config cfg;

    @BeforeClass
    public static void initializeConfig() {
        System.setProperty("cachingDisabled", "true");
        cfg = ConfigManager.getInstance().getConfig();
        sqlConfig = ConfigFactory.parseResources(ConfigFile.SQL_CONF);
    }
}
