package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import org.broadinstitute.lddp.email.Recipient;

@Data
public class FollowUpSurvey extends SimpleFollowUpSurvey {
    private boolean generateNow = false;

    public FollowUpSurvey() {
    }

    public FollowUpSurvey(String participantId) {
        super(participantId);
    }

    public FollowUpSurvey(String participantId, long triggerId) {
        super(participantId, triggerId);
    }

    public FollowUpSurvey(String participantId, Recipient recipient, long triggerId) {
        super(participantId, recipient, triggerId);
    }
}

