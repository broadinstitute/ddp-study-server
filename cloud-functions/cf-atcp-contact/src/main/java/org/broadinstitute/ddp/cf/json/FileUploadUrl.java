package org.broadinstitute.ddp.cf.json;

import com.google.gson.annotations.SerializedName;

public class FileUploadUrl {

    @SerializedName("url")
    private final String url;

    public FileUploadUrl(String url) {
        this.url = url;
    }
}
