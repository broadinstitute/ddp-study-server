package org.broadinstitute.ddp.housekeeping.message;

import com.google.gson.annotations.SerializedName;

public class CreateKitMessage implements HousekeepingMessage {
    @SerializedName("participantGuid")
    private String participantGuid;

    @SerializedName("studyGuid")
    private String studyGuid;

    @SerializedName("eventConfigId")
    private long eventConfigurationId;

    public CreateKitMessage(String participantGuid,
                            String studyGuid,
                            long eventConfigurationId) {
        this.participantGuid = participantGuid;
        this.studyGuid = studyGuid;
        this.eventConfigurationId = eventConfigurationId;
    }

    @Override
    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

}
