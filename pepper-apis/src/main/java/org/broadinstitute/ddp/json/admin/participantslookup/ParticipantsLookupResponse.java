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
    private List<ParticipantsLookupResultRow> participants;

    public ParticipantsLookupResponse(int totalCount, List<ParticipantsLookupResultRow> participants) {
        this.totalCount = totalCount;
        this.participants = participants;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public List<ParticipantsLookupResultRow> getParticipants() {
        return participants;
    }
}
