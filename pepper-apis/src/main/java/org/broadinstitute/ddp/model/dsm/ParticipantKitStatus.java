package org.broadinstitute.ddp.model.dsm;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Representation of participant's list of kit statuses. Used in batch response from DSM.
 */
public class ParticipantKitStatus {

    @SerializedName("participantId")
    private String participantGuid;
    @SerializedName("samples")
    private List<ParticipantStatus.Sample> samples;

    public ParticipantKitStatus(String participantGuid, List<ParticipantStatus.Sample> samples) {
        this.participantGuid = participantGuid;
        this.samples = new ArrayList<>(samples);
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public List<ParticipantStatus.Sample> getSamples() {
        return samples == null ? new ArrayList<>() : samples;
    }
}
