package org.broadinstitute.ddp.handlers.util;

import lombok.Data;

@Data
public class MedicalInfo extends ParticipantInstitutionList
{
    public MedicalInfo() {
    }


    public MedicalInfo(String participantId) {
        super(participantId);
    }

    private String dob;
    private String dateOfDiagnosis;
    private Integer drawBloodConsent;
    private Integer tissueSampleConsent;
}

