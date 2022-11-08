package org.broadinstitute.ddp.util;

import java.util.Optional;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PropertyManager {
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public enum Property {
        GOOGLE_SECRET_PROJECT("google.secret.project"),
        GOOGLE_SECRET_VERSION("google.secret.version"),
        GOOGLE_SECRET_NAME("google.secret.name"),
        GOOGLE_SECRET_LICENSE("google.secret.license");

        private final String key;
    }

    public static Optional<String> getProperty(final Property property) {
        return getProperty(property.getKey());
    }

    public static Optional<String> getProperty(final String propertyName) {
        return Optional.ofNullable(System.getProperty(propertyName));
    }

    public static String getProperty(final Property property, final String defaultValue) {
        return getProperty(property.getKey(), defaultValue);
    }

    public static String getProperty(final String propertyName, final String defaultValue) {
        return Optional.ofNullable(System.getProperty(propertyName)).orElse(defaultValue);
    }
}
