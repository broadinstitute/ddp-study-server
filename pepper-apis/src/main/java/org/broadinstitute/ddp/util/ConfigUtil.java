package org.broadinstitute.ddp.util;

import static org.slf4j.LoggerFactory.getLogger;

import java.time.Instant;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigRenderOptions;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.slf4j.Logger;

public class ConfigUtil {

    private static final Logger LOG = getLogger(ConfigUtil.class);

    /**
     * Returns the sendgrid templates used for testing.  Don't call this
     * outside of testing, as production code looks up templates from the db.
     */
    public static Config getTestingSendgridTemplates(Config cfg) {
        return cfg.getConfig(ConfigFile.Sendgrid.TEMPLATES);
    }

    /**
     * Returns the general purpose sendgrid template used for testing.  Don't call this
     * outside of testing, as production code looks up templates from the db.
     */
    public static Config getGenericSendgridTestingTemplate(Config cfg) {
        return getTestingSendgridTemplates(cfg).getConfig("emailTesting");
    }

    /**
     * Get value as string if the key is present and value is not null.
     */
    public static String getStrIfPresent(Config cfg, String key) {
        return cfg.hasPath(key) ? cfg.getString(key) : null;
    }

    /**
     * Get value as int if the key is present and value is not null.
     */
    public static Integer getIntIfPresent(Config cfg, String key) {
        return cfg.hasPath(key) ? cfg.getInt(key) : null;
    }

    /**
     * Get value as long if the key is present and value is not null.
     */
    public static Long getLongIfPresent(Config cfg, String key) {
        return cfg.hasPath(key) ? cfg.getLong(key) : null;
    }

    /**
     * Get value as boolean if the key is present and value is not null.
     */
    public static Boolean getBoolIfPresent(Config cfg, String key) {
        return cfg.hasPath(key) ? cfg.getBoolean(key) : null;
    }

    /**
     * Get value as boolean if the key is present and value is not null.
     * If key not present then return a default value.
     */
    public static boolean getBoolIfPresent(Config cfg, String key, boolean defaultValue) {
        try {
            return cfg.hasPath(key) ? cfg.getBoolean(key) : defaultValue;
        } catch (ConfigException e) {
            String value = e instanceof ConfigException.WrongType ? "=" + cfg.getValue(key) : "";
            LOG.error("Error during reading config key: " + key + value, e);
            return defaultValue;
        }
    }

    /**
     * Get and parse value as an Instant if the key is present and value is not null.
     * Value should be a string with format as specified by {@code DateTimeFormatter.ISO_INSTANT}.
     */
    public static Instant getInstantIfPresent(Config cfg, String key) {
        return cfg.hasPath(key) ? Instant.parse(cfg.getString(key)) : null;
    }

    /**
     * Convert the config object into a JSON string.
     */
    public static String toJson(Config cfg) {
        return cfg.root().render(ConfigRenderOptions.concise());
    }


}
