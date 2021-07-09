package org.broadinstitute.ddp.migration;

import com.google.gson.annotations.SerializedName;

/**
 * Represents mapping for dsm field data.
 */
public class MappingDsmField {

    @SerializedName("source")
    private String source;
    @SerializedName("target")
    private String target;

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }
}
