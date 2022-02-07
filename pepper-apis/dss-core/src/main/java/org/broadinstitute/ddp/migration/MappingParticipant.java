package org.broadinstitute.ddp.migration;

import com.google.gson.annotations.SerializedName;

/**
 * Represents configuration options for mapping participants.
 */
public class MappingParticipant {

    @SerializedName("default_language")
    private String defaultLanguage;

    public String getDefaultLanguage() {
        return defaultLanguage;
    }
}
