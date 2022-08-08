package org.broadinstitute.dsm.util;

import com.typesafe.config.Config;

import lombok.Getter;
import lombok.NonNull;

public class DSMConfig {
    /**
     * The shared instance to use for the static methods provided by
     * this class.
     * 
     * <p>This instance variable and any associated methods are not guaranteed thread-safe.
     */
    private static DSMConfig sharedInstance;

    @Getter
    private Config config;

    public DSMConfig(Config config) {
        this.config = config;

        // This method is only called once, so a less-efficient, but simpler, locking
        //  pattern is being used.
        synchronized(DSMConfig.class) {
            if (sharedInstance == null) {
                DSMConfig.sharedInstance = this;
            }
        }
    }

    public static String getSqlFromConfig(@NonNull String queryName) {
        var config = DSMConfig.sharedInstance.getConfig();

        if (config == null) {
            throw new RuntimeException("Config is null ");
        }

        if (!config.hasPath(queryName)) {
            throw new RuntimeException("Conf is missing query named " + queryName);
        }

        return config.getString(queryName);
    }

    public static String getStringIfPresent(@NonNull String queryName) {
        var config = DSMConfig.sharedInstance.getConfig();

        if (!config.hasPath(queryName)) {
            return null;
        }

        return config.getString(queryName);
    }

    public static boolean hasConfigPath(@NonNull String configPath) {
        var config = DSMConfig.sharedInstance.getConfig();

        if (config == null) {
            throw new RuntimeException("Conf has not been configured");
        } else {
            return config.hasPath(configPath);
        }
    }
}
