package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;

public class PdfConfigRecord {

    @SerializedName("configName")
    private String configName;
    @SerializedName("displayName")
    private String displayName;

    public PdfConfigRecord(String configName, String displayName) {
        this.configName = configName;
        this.displayName = displayName;
    }

}
