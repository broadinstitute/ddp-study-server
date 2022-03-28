package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class UserRegistrationResponse {
    @SerializedName("ddpUserGuid")
    String ddpUserGuid;
}
