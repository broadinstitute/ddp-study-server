package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import lombok.NonNull;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantExit;

public class ParticipantExitDecorator {

    public static Map<String, Participant> getParticipants(@NonNull String realm) {
        Map<String, Participant> participants = Participant.getParticipants(realm);
        Map<String, ParticipantExit> exitedParticipants = ParticipantExit.getExitedParticipants(realm, false);
        for (Map.Entry<String, ParticipantExit> entry : exitedParticipants.entrySet()) {
            Participant participant = new Participant();
            String ddpParticipantId = entry.getKey();
            participant.setDdpParticipantId(ddpParticipantId);
            participant.setExitDate(entry.getValue().getExitDate());
            participants.putIfAbsent(ddpParticipantId, participant);
        }
        return participants;
    }
}
