package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class CreateStudyParticipantResponse {

    @SerializedName("userGuid")
    private String userGuid;

    public CreateStudyParticipantResponse(String userGuid) {
        this.userGuid = userGuid;
    }

    public String getUserGuid() {
        return userGuid;
    }
}
