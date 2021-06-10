package org.broadinstitute.ddp.customexport.model;

import org.broadinstitute.ddp.model.study.Participant;

public class CustomExportParticipant {
    private final String familyId;
    private final Participant participant;

    public CustomExportParticipant(String familyId, Participant participant)  {
        this.familyId = familyId;
        this.participant = participant;
    }

    public Participant getParticipant() {
        return participant;
    }

    public String getFamilyId() {
        return familyId;
    }
}
