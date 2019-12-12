package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class DeployedAppVersionResponse {
    @SerializedName("backendVersion")
    private String backendVersion;
    @SerializedName("appSHA")
    private String appSHA;

    public DeployedAppVersionResponse(String backendVersion, String appSHA) {
        this.backendVersion = backendVersion;
        this.appSHA = appSHA;
    }
}
