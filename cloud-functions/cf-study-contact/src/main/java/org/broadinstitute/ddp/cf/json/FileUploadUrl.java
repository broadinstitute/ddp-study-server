package org.broadinstitute.ddp.cf.json;

import com.google.gson.annotations.SerializedName;

public class FileUploadUrl {
    @SerializedName("url")
    String url;

    public FileUploadUrl(String url) {
        this.url = url;
    }
}
