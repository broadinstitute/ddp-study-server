package org.broadinstitute.dsm.util;

import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.dsm.exception.DsmInternalError;

public class DSMConfig {

    private static Config config;

    public DSMConfig(Config config) {
        this.config = config;
    }

    public static String getSqlFromConfig(@NonNull String queryName) {
        if (config == null) {
            throw new RuntimeException("Config is null ");
        }

        if (!config.hasPath(queryName)) {
            throw new RuntimeException("Conf is missing query named " + queryName);
        }

        return config.getString(queryName);
    }

    public static Integer getIntFromConfig(@NonNull String name) {
        try {
            if (hasConfigPath(name)) {
                return config.getInt(name);
            }
            return null;
        } catch (Exception e) {
            throw new DsmInternalError("Error getting int from config for " + name, e);
        }
    }

    public static Double getDoubleFromConfig(@NonNull String name) {
        try {
            if (hasConfigPath(name)) {
                return config.getDouble(name);
            }
            return null;
        } catch (Exception e) {
            throw new DsmInternalError("Error getting double from config for " + name, e);
        }
    }

    public static boolean hasConfigPath(@NonNull String configPath) {
        if (configPath == null) {
            throw new NullPointerException("configPath");
        } else {
            if (config == null) {
                throw new RuntimeException("Conf has not been configured");
            } else {
                return config.hasPath(configPath);
            }
        }
    }
}
