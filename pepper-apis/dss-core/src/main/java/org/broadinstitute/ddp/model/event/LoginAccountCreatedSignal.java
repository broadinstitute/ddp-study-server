package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.model.activity.types.EventTriggerType;

public class LoginAccountCreatedSignal extends EventSignal {

    private String passwordResetTicketUrl;

    public LoginAccountCreatedSignal(long operatorId, long participantId, String participantGuid,
                                     String operatorGuid, long studyId, String studyGuid, String passwordResetTicketUrl) {
        super(operatorId, participantId, participantGuid, operatorGuid, studyId, studyGuid, EventTriggerType.LOGIN_ACCOUNT_CREATED);
        this.passwordResetTicketUrl = passwordResetTicketUrl;
    }

    public String getPasswordResetTicketUrl() {
        return passwordResetTicketUrl;
    }
}
