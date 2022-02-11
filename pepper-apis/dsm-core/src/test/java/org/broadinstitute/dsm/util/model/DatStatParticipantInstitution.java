package org.broadinstitute.dsm.util.model;

import org.broadinstitute.dsm.model.ddp.DDPParticipant;

public class DatStatParticipantInstitution {

    private DDPParticipant ddpParticipant;
    private DatStatInstitution institution;

    public DatStatParticipantInstitution(DDPParticipant ddpParticipant, DatStatInstitution institution){
        this.ddpParticipant = ddpParticipant;
        this.institution = institution;
    }

    public DDPParticipant getDdpParticipant() {
        return ddpParticipant;
    }

    public DatStatInstitution getInstitution() {
        return institution;
    }
}