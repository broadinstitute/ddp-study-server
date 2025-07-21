package org.broadinstitute.dsm.model.ddp;

import lombok.Data;

@Data
public class KitDetail {

    private String participantId;
    private String kitRequestId;
    private String kitType;
    private boolean needsApproval;
    private String sexAtBirth;

    public KitDetail(String participantId, String kitRequestId, String kitType, boolean needsApproval,
                     String sexAtBirth) {
        this.participantId = participantId;
        this.kitRequestId = kitRequestId;
        this.kitType = kitType;
        this.needsApproval = needsApproval;
        this.sexAtBirth = sexAtBirth;
    }
}
