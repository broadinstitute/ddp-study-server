package org.broadinstitute.dsm.model.gp;

import lombok.Data;
import org.broadinstitute.dsm.db.dto.kit.ClinicalKitDto;

@Data
public class ClinicalKitWrapper {
    ClinicalKitDto clinicalKitDto;
    Integer ddpInstanceId;
    String ddpParticipantId;

    public ClinicalKitWrapper(ClinicalKitDto clinicalKitDto, int ddpInstanceId, String ddpParticipantId) {
        this.clinicalKitDto = clinicalKitDto;
        this.ddpInstanceId = ddpInstanceId;
        this.ddpParticipantId = ddpParticipantId;
    }
}
