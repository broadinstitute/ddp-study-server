package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class FileUploadUrlResponse {
    @SerializedName("url")
    String url;

    @SerializedName("guid")
    String guid;

    @SerializedName("httpMethod")
    String httpMethod;

    public FileUploadUrlResponse(String url, String guid, String httpMethod) {
        this.url = url;
        this.guid = guid;
        this.httpMethod = httpMethod;
    }
}
