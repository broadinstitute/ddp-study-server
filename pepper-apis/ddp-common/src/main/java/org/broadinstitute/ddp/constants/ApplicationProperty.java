package org.broadinstitute.ddp.constants;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ApplicationProperty {
    GOOGLE_SECRET_PROJECT("google.secret.project"),
    GOOGLE_SECRET_VERSION("google.secret.version"),
    GOOGLE_SECRET_NAME("google.secret.name"),
    ITEXT_LICENSE("itext.license"),
    APPLICATION_CONFIG("config.file");

    private final String propertyName;
}
