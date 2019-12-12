package org.broadinstitute.ddp.security;

import java.util.ArrayList;
import java.util.Collection;

import com.google.gson.annotations.SerializedName;

public class ParticipantAccess {

    @SerializedName("p")
    private String participantGuid = "";

    @SerializedName("s")
    private Collection<String> studyGuids = new ArrayList<>();

    public ParticipantAccess(String participantGuid) {
        this.participantGuid = participantGuid;
    }

    public void addStudyGuid(String studyGuid) {
        this.studyGuids.add(studyGuid);
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public Collection<String> getStudyAccess() {
        return studyGuids;
    }
}
