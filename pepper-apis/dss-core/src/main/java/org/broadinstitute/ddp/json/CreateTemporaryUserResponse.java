package org.broadinstitute.ddp.json;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.transformers.MillisToIsoInstantAdapter;

public class CreateTemporaryUserResponse {

    @SerializedName("userGuid")
    private String userGuid;

    @JsonAdapter(MillisToIsoInstantAdapter.class)
    @SerializedName("expiresAt")
    private Long expiresAt;

    public CreateTemporaryUserResponse(String userGuid, long expiresAt) {
        this.userGuid = userGuid;
        this.expiresAt = expiresAt;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}
