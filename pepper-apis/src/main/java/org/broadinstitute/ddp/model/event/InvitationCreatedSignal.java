package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;

public class InvitationCreatedSignal extends EventSignal {

    private InvitationDto invitationDto;

    public InvitationCreatedSignal(long operatorId, long participantId, String participantGuid,
                                   String operatorGuid, long studyId, InvitationDto invitationDto) {
        super(operatorId, participantId, participantGuid, operatorGuid, studyId, EventTriggerType.INVITATION_CREATED);
        this.invitationDto = invitationDto;
    }

    public InvitationDto getInvitationDto() {
        return invitationDto;
    }

    @Override
    public String toString() {
        return "InvitationCreatedSignal{"
                + "invitationId=" + invitationDto.getInvitationId()
                + ", invitationGuid=" + invitationDto.getInvitationGuid()
                + ", invitationContactEmail=" + invitationDto.getContactEmail()
                + ", invitationCreatedAt=" + invitationDto.getCreatedAt()
                + '}';
    }
}
