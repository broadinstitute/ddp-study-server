package org.broadinstitute.ddp.customexport.model;

import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.model.study.Participant;
import org.broadinstitute.ddp.model.user.User;

public class CustomExportParticipant extends Participant {
    private final String familyId;

    public CustomExportParticipant(String familyId, EnrollmentStatusDto status, User user) {
        super(status, user);
        this.familyId = familyId;
    }

    public String getFamilyId() {
        return familyId;
    }
}
