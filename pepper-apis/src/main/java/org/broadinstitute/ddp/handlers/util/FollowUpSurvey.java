package org.broadinstitute.ddp.handlers.util;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.util.CheckValidity;

@Data
public class FollowUpSurvey extends SimpleFollowUpSurvey
{
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

    private boolean generateNow = false;
}

