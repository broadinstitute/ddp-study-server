package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class UpdateInvitationPayload {

    @SerializedName("notes")
    private String notes;

    public UpdateInvitationPayload(String notes) {
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }
}
