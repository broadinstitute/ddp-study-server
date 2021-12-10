package org.broadinstitute.lddp.handlers.util;

import java.util.ArrayList;

/**
 * Created by ebaker on 5/4/17.
 */
public class ParticipantInstitutionList {
    public ParticipantInstitutionList() {
    }

    private String participantId; //UUID (alt pid)
    private ArrayList<InstitutionDetail> institutions; //institutions

    public ParticipantInstitutionList(String participantId) {
        this.participantId = participantId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public ArrayList<InstitutionDetail> getInstitutions() {
        return institutions;
    }

    public void setInstitutions(ArrayList<InstitutionDetail> institutions) { this.institutions = institutions; }
}
