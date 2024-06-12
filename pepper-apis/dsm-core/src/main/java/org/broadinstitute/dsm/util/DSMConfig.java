package org.broadinstitute.dsm.util;

import com.typesafe.config.Config;
import lombok.NonNull;

public class DSMConfig {

    private static Config config;

    public DSMConfig(Config config) {
        this.config = config;
    }

    public static String getSqlFromConfig(@NonNull String queryName) {
        checkNullConfig();
        if (!config.hasPath(queryName)) {
            throw new RuntimeException("Conf is missing query named " + queryName);
        }
        return config.getString(queryName);
    }

    private static void checkNullConfig() {
        if (config == null) {
            throw new RuntimeException("Config is null ");
        }
    }

    public static int getIntFromConfig(String path) {
        checkNullConfig();
        return config.getInt(path);
    }

    public static double getDoubleFromConfig(String path) {
        checkNullConfig();
        return config.getDouble(path);
    }

    public static boolean hasConfigPath(@NonNull String configPath) {
        if (configPath == null) {
            throw new NullPointerException("configPath");
        } else {
            checkNullConfig();
            return config.hasPath(configPath);
        }
    }
}
