package org.broadinstitute.ddp.model.migration;

import com.google.gson.annotations.SerializedName;

public class ParticipantData {

    @SerializedName("user")
    private ParticipantUser participantUser;

    public ParticipantUser getParticipantUser() {
        return participantUser;
    }

    public void setParticipantUser(ParticipantUser participantUser) {
        this.participantUser = participantUser;
    }

}
