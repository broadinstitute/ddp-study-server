package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class FileUploadUrlResponse {
    @SerializedName("url")
    String url;

    @SerializedName("guid")
    String guid;

    @SerializedName("resumable")
    boolean resumable;

    public FileUploadUrlResponse(String url, String guid, boolean resumable) {
        this.url = url;
        this.guid = guid;
        this.resumable = resumable;
    }
}
