package org.broadinstitute.ddp.json.admin.participantslookup;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.route.ParticipantsLookupRoute;

/**
 * Result of {@link ParticipantsLookupRoute}.
 */
public class ParticipantsLookupResponse {

    @SerializedName("totalCount")
    private int totalCount;

    @SerializedName("results")
    private List<ParticipantsLookupResultRow> results;

    public ParticipantsLookupResponse(int totalCount, List<ParticipantsLookupResultRow> results) {
        this.totalCount = totalCount;
        this.results = results;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public List<ParticipantsLookupResultRow> getResults() {
        return results;
    }
}
