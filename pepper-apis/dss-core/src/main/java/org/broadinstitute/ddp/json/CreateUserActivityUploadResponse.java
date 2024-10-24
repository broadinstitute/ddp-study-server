package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class CreateUserActivityUploadResponse {
    @SerializedName("uploadGuid")
    String uploadGuid;
    
    @SerializedName("uploadUrl")
    String uploadUrl;
}
