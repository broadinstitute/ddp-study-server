package org.broadinstitute.dsm.model.elastic;

import org.broadinstitute.dsm.db.Participant;

public class NewOsteoParticipant extends Participant {

    public NewOsteoParticipant(Long participantId, String ddpParticipantId, String assigneeIdMr, Integer ddpInstanceId, String assigneeIdTissue, String realm, String created, String reviewed, String crSent, String crReceived, String notes, Boolean minimalMr, Boolean abstractionReady, String additionalValuesJson, Long exitDate) {
        super(participantId, ddpParticipantId, assigneeIdMr, ddpInstanceId, assigneeIdTissue, realm, created, reviewed, crSent, crReceived, notes, minimalMr, abstractionReady, additionalValuesJson, exitDate);
    }

    // empty constructor needed for jackson library
    public NewOsteoParticipant() {}

    public static NewOsteoParticipant copy(Participant that, Long newParticipantId, Integer newDdpInstanceId) {
        return new NewOsteoParticipant(
                newParticipantId,
                that.getDdpParticipantId(),
                that.getAssigneeIdMr(),
                newDdpInstanceId,
                that.getAssigneeIdTissue(),
                that.getRealm(),
                that.getCreated(),
                that.getReviewed(),
                that.getCrSent(),
                that.getCrReceived(),
                that.getNotes(),
                that.getMinimalMr(),
                that.getAbstractionReady(),
                that.getAdditionalValuesJson(),
                that.getExitDate());
    }
}
