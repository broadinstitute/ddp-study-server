package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.dsm.service.participantdata.DuplicateParticipantData;

/**
 * Log entry for various AdminOperations
 */
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateLog {

    public enum UpdateStatus {
        UPDATED,
        NOT_UPDATED,
        NA_REFERRAL_SOURCE,
        ERROR,
        NO_ACTIVITIES,
        NO_REFERRAL_SOURCE_IN_ACTIVITY,
        NO_PARTICIPANT_DATA,
        ES_UPDATED,
        DUPLICATE_PARTICIPANT_DATA
    }

    private final String ddpParticipantId;
    private UpdateStatus status;
    private String message;
    private List<DuplicateParticipantData> duplicateParticipantData;

    public UpdateLog(String ddpParticipantId, UpdateStatus status) {
        this.ddpParticipantId = ddpParticipantId;
        this.status = status;
    }

    public UpdateLog(String ddpParticipantId, UpdateStatus status, String message) {
        this.ddpParticipantId = ddpParticipantId;
        this.status = status;
        this.message = message;
    }

    public void setError(String message) {
        this.message = message;
        this.status = UpdateStatus.ERROR;
    }

    public void setDuplicateParticipantData(List<DuplicateParticipantData> duplicateData) {
        this.duplicateParticipantData = duplicateData;
        this.status = UpdateStatus.DUPLICATE_PARTICIPANT_DATA;
    }
}
