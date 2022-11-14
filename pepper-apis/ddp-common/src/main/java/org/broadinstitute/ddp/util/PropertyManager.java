package org.broadinstitute.ddp.util;

import java.util.Optional;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ApplicationProperty;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PropertyManager {
    public static Optional<String> getProperty(final ApplicationProperty property) {
        return getProperty(property.getPropertyName());
    }

    public static Optional<String> getProperty(final String propertyName) {
        return Optional.ofNullable(System.getProperty(propertyName));
    }

    public static String getProperty(final ApplicationProperty property, final String defaultValue) {
        return getProperty(property.getPropertyName(), defaultValue);
    }

    public static String getProperty(final String propertyName, final String defaultValue) {
        return Optional.ofNullable(System.getProperty(propertyName)).orElse(defaultValue);
    }
}
