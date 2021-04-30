package org.broadinstitute.ddp.json.admin.participantslookup;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class ParticipantsLookupPayload {

    @SerializedName("query")
    private String query;

    public ParticipantsLookupPayload(@NotEmpty String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}
