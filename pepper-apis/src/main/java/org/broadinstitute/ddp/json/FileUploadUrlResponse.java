package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class FileUploadUrlResponse {
    @SerializedName("url")
    String url;

    @SerializedName("guid")
    String guid;

    public FileUploadUrlResponse(String url, String guid) {
        this.url = url;
        this.guid = guid;
    }
}
