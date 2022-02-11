package org.broadinstitute.dsm.model;

import lombok.Getter;

@Getter
public class ParticipantSurveyUploadObject {

    private String ddpParticipantId;
    private String shortId;
    private String firstName;
    private String lastName;
    private String email;

    public ParticipantSurveyUploadObject(String ddpParticipantId) {
        this.ddpParticipantId = ddpParticipantId;
    }

    public ParticipantSurveyUploadObject(String shortId, String firstName, String lastName, String email) {
        this.shortId = shortId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    public String getDDPParticipantID() {
        if (ddpParticipantId != null) {
            return ddpParticipantId;
        }
        else if (shortId != null) {
            return shortId;
        }
        return null;
    }
}
