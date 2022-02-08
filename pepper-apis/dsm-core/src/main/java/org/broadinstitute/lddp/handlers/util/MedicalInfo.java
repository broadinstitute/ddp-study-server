package org.broadinstitute.lddp.handlers.util;

import lombok.Data;

@Data
public class MedicalInfo extends ParticipantInstitutionList {
    private String dob;
    private String dateOfDiagnosis;
    private Integer drawBloodConsent;
    private Integer tissueSampleConsent;

    public MedicalInfo() {
    }

    public MedicalInfo(String participantId) {
        super(participantId);
    }
}

