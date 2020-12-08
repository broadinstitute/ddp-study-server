package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class FileUploadURLResponse {
    @SerializedName("url")
    String url;

    @SerializedName("guid")
    String guid;

    public FileUploadURLResponse(String url, String guid) {
        this.url = url;
        this.guid = guid;
    }
}
