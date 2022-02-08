package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class CreateUserActivityUploadResponse {

    @SerializedName("uploadGuid")
    private String uploadGuid;
    @SerializedName("uploadUrl")
    private String uploadUrl;

    public CreateUserActivityUploadResponse(String uploadGuid, String uploadUrl) {
        this.uploadGuid = uploadGuid;
        this.uploadUrl = uploadUrl;
    }

    public String getUploadGuid() {
        return uploadGuid;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }
}
