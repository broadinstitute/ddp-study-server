package org.broadinstitute.dsm.util;

import com.typesafe.config.Config;
import lombok.NonNull;

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
}
