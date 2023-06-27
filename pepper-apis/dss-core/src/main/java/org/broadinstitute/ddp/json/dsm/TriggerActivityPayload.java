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

    @SerializedName("resultsFilePath")
    private String resultsFilePath;

    @SerializedName("bucketName")
    private String bucketName;

    public TriggerActivityPayload(String participantGuid, Long triggerId) {
        this.participantGuid = participantGuid;
        this.triggerId = triggerId;
    }

    public TriggerActivityPayload(String participantGuid, Long triggerId, String resultsFilePath) {
        this.participantGuid = participantGuid;
        this.triggerId = triggerId;
        this.resultsFilePath = resultsFilePath;
    }

    public TriggerActivityPayload(String participantGuid, Long triggerId, String resultsFilePath, String bucketName) {
        this.participantGuid = participantGuid;
        this.triggerId = triggerId;
        this.resultsFilePath = resultsFilePath;
        this.bucketName = bucketName;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public Long getTriggerId() {
        return triggerId;
    }

    public String getResultsFilePath() {
        return resultsFilePath;
    }

    public String getBucketName() {
        return bucketName;
    }

}
