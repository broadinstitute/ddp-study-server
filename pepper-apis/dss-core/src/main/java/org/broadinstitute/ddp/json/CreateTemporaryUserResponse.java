package org.broadinstitute.ddp.json;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.transformers.MillisToIsoInstantAdapter;

@Value
@AllArgsConstructor
public class CreateTemporaryUserResponse {
    @SerializedName("userGuid")
    String userGuid;

    @JsonAdapter(MillisToIsoInstantAdapter.class)
    @SerializedName("expiresAt")
    Long expiresAt;
}
