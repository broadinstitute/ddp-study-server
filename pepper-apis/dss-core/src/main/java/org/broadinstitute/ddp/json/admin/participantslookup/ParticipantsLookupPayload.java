package org.broadinstitute.ddp.json.admin.participantslookup;

import javax.validation.constraints.Size;

import com.google.gson.annotations.SerializedName;

public class ParticipantsLookupPayload {

    @Size(max = 100)
    @SerializedName("query")
    private String query;

    public ParticipantsLookupPayload(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}
