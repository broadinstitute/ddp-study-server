package org.broadinstitute.ddp.util;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import org.apache.commons.io.FileUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper around {@link ConfigFactory#load() config load} that
 * allows for on-the-fly overrides of config values during testing.
 * DO NOT CALL {@link #overrideValue(String, String)} unless you
 * are in a test!
 */
public class ConfigManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigManager.class);

    private static final String TYPESAFE_CONFIG_SYSTEM_VAR = "config.file";

    public static File TYPESAFE_CONFIG_FILE = new File(System.getProperty(TYPESAFE_CONFIG_SYSTEM_VAR));

    private final Config cfg;

    private static ConfigManager configManager;

    private final Map<String, String> overrides = new HashMap<>();

    private ConfigManager(Config cfg) {
        this.cfg = cfg;
    }

    /**
     * The one true way to get the {@link Config} value for pepper.
     * @return the config for the app
     */
    public static synchronized ConfigManager getInstance() {
        if (configManager == null) {
            try {
                configManager = new ConfigManager(parseConfig());
            } catch (Exception e) {
                throw new DDPException("Could not initialize config", e);
            }
        }
        return configManager;
    }

    /**
     * Re-reads the main config file directly, bypassing any typesafe config caching.  Useful
     * for responding to changes to the config file without a reboot.
     * Unless you are specifically looking to re-read directly from disk,
     * please use {@link #getInstance()}
     */
    public static Config parseConfig() {
        return ConfigFactory.parseFile(TYPESAFE_CONFIG_FILE);
    }

    /**
     * Reads the config file to a string
     */
    public static String readConfigFile() throws IOException {
        return FileUtils.readFileToString(TYPESAFE_CONFIG_FILE);
    }

    /**
     * Overwrites the config file.  ONLY USE IN TESTS.
     */
    public static void rewriteConfigFile(String newContents) throws IOException  {
        FileUtils.writeStringToFile(TYPESAFE_CONFIG_FILE, newContents);
    }

    /**
     * Overwrites the config file.  ONLY USE IN TESTS.
     */
    public static void rewriteConfigFile(Config newConfig) throws IOException  {
        FileUtils.writeStringToFile(TYPESAFE_CONFIG_FILE, newConfig.root().render(ConfigRenderOptions.concise()));
    }

    /**
     * Returns the current overrides
     */
    public Map<String, String> getOverrides() {
        return Collections.unmodifiableMap(overrides);
    }

    /**
     * Should only be called from tests to inject
     * and override for the given path.  Should
     * only be used from test code!
     */
    public void overrideValue(String path, String value) {
        overrides.put(path, value);
    }

    /**
     * Returns the {@link Config config} for pepper
     */
    public Config getConfig() {
        Config cfgWithOverride = cfg;

        for (Map.Entry<String, String> override : overrides.entrySet()) {
            ConfigValue overrideValue = ConfigValueFactory.fromAnyRef(override.getValue(), "Overriding via " + this.getClass().getName());
            cfgWithOverride = cfgWithOverride.withValue(override.getKey(), overrideValue);
            LOG.info("Overriding config value {} with {}", override.getKey(),  override.getValue());
        }
        return cfgWithOverride;
    }

}
