package org.broadinstitute.ddp.json.dsm;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

public class TriggerActivityPayload {

    @NotBlank
    @SerializedName("participantId")
    private String participantGuid;

    @NotNull
    @SerializedName("triggerId")
    private Long triggerId;

    public TriggerActivityPayload(String participantGuid, Long triggerId) {
        this.participantGuid = participantGuid;
        this.triggerId = triggerId;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public Long getTriggerId() {
        return triggerId;
    }
}
