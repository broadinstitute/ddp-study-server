package org.broadinstitute.dsm.service.adminoperation;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class UpdateLog {
    private final String ddpParticipantId;
    private final String status;
    private String message;

    public UpdateLog(String ddpParticipantId, String status) {
        this.ddpParticipantId = ddpParticipantId;
        this.status = status;
    }
}
