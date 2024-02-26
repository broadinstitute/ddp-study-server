package org.broadinstitute.dsm.service.adminoperation;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Log entry for various AdminOperations
 */
@AllArgsConstructor
@Data
public class UpdateLog {

    public enum UpdateStatus {
        UPDATED,
        NOT_UPDATED,
        NA_REFERRAL_SOURCE,
        ERROR,
        NO_ACTIVITIES,
        NO_REFERRAL_SOURCE_IN_ACTIVITY,
        NO_PARTICIPANT_DATA,
        ES_UPDATED
    }

    private final String ddpParticipantId;
    private String status;
    private String message;

    public UpdateLog(String ddpParticipantId, String status) {
        this.ddpParticipantId = ddpParticipantId;
        this.status = status;
    }

    public void setError(String message) {
        this.message = message;
        this.status = UpdateStatus.ERROR.name();
    }
}
