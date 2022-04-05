package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import org.broadinstitute.lddp.email.Recipient;

@Data
public class SimpleFollowUpSurvey {
    private String participantId;
    private Recipient recipient;
    private Long triggerId;

    public SimpleFollowUpSurvey() {
    }

    public SimpleFollowUpSurvey(String participantId) {
        this.participantId = participantId;
    }

    public SimpleFollowUpSurvey(String participantId, long triggerId) {
        this.participantId = participantId;
        this.triggerId = triggerId;
    }

    public SimpleFollowUpSurvey(String participantId, Recipient recipient, long triggerId) {
        this.participantId = participantId;
        this.recipient = recipient;
        this.triggerId = triggerId;
    }

}
