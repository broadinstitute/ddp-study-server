package org.broadinstitute.ddp.json.admin.participantslookup;

import com.google.gson.annotations.SerializedName;

public class ParticipantsLookupPayload {

    @SerializedName("query")
    private String query;

    public ParticipantsLookupPayload(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}
