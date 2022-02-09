package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.lddp.email.Recipient;
import org.broadinstitute.lddp.util.CheckValidity;

@Data
public class SimpleFollowUpSurvey implements CheckValidity {
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

    public boolean isValid() {
        return (StringUtils.isNotBlank(participantId));
    }
}

