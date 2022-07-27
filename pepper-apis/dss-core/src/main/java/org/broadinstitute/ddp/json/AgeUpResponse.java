package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class AgeUpResponse {
    @SerializedName("userGuid")
    private String userGuid;

    @SerializedName("hasAgedUp")
    private boolean hasAgedUp;

    public AgeUpResponse(String userGuid, boolean hasAgedUp) {
        this.userGuid = userGuid;
        this.hasAgedUp = hasAgedUp;
    }
}
