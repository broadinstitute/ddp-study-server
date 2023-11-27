package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class DeployedAppVersionResponse {
    @SerializedName("backendVersion")
    String backendVersion;
    
    @SerializedName("appSHA")
    String appSHA;
}
