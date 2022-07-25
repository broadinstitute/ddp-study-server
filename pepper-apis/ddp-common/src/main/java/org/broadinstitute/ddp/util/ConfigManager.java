package org.broadinstitute.ddp.util;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.secrets.SecretManager;

/**
 * Wrapper around {@link ConfigFactory#load() config load} that
 * allows for on-the-fly overrides of config values during testing.
 * DO NOT CALL {@link #overrideValue(String, String)} unless you
 * are in a test!
 */
@Slf4j
public class ConfigManager {
    private static final String GOOGLE_SECRET_PROJECT = "google.secret.project";
    private static final String GOOGLE_SECRET_NAME = "google.secret.name";
    private static final String GOOGLE_SECRET_VERSION = "google.secret.version";

    private static final String TYPESAFE_CONFIG_SYSTEM_VAR = "config.file";
    public static final File TYPESAFE_CONFIG_FILE;

    static {
        // For benefit of GAE. Does not like command line options with "=" characters and env variables with "."
        String configFileName = System.getenv(TYPESAFE_CONFIG_SYSTEM_VAR.replace('.', '_'));
        if (configFileName == null) {
            configFileName = System.getProperty(TYPESAFE_CONFIG_SYSTEM_VAR);
        }
        TYPESAFE_CONFIG_FILE = configFileName != null ? new File(configFileName) : null;
    }

    private final Config cfg;

    private static ConfigManager configManager;

    private final Map<String, String> overrides = new HashMap<>();

    private ConfigManager(Config cfg) {
        this.cfg = cfg;
    }

    public static ConfigManager init(Config config) {
        configManager = new ConfigManager(config);
        return configManager;
    }

    public static synchronized ConfigManager getInstance() {
        if (configManager == null) {
            try {
                init(parseConfig());
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
        if (TYPESAFE_CONFIG_FILE != null) {
            log.info("The config file name was specified. Loading configuration from the local file");
            return ConfigFactory.parseFile(TYPESAFE_CONFIG_FILE);
        }

        log.info("The config file name was not specified. Loading configuration from Secrets Storage");

        final var projectName = getProperty(GOOGLE_SECRET_PROJECT);
        if (projectName == null) {
            log.error(GOOGLE_SECRET_PROJECT + " property is not set");
        }

        final var secretName = getProperty(GOOGLE_SECRET_NAME);
        if (secretName == null) {
            log.error(GOOGLE_SECRET_NAME + " property is not set");
        }

        if (projectName == null && secretName == null) {
            return null;
        }

        final var secretVersion = getProperty(GOOGLE_SECRET_VERSION, "latest");
        return ConfigFactory.parseString(SecretManager.get(projectName, secretName, secretVersion).orElseThrow());
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
            log.info("Overriding config value for key {}", override.getKey());
        }
        return cfgWithOverride;
    }

    private static String getProperty(final String propertyName) {
        return getProperty(propertyName, null);
    }

    private static String getProperty(final String propertyName, final String defaultValue) {
        return Optional.ofNullable(System.getProperty(propertyName)).orElse(defaultValue);
    }
}
