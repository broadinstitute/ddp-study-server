package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import lombok.NonNull;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantExit;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class ParticipantMigrator extends BaseSingleMigrator implements Exportable, Generator {

    public ParticipantMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.PARTICIPANT);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        return (Map) ParticipantExitDecorator.getParticipants(realm);
    }

}

class ParticipantExitDecorator {

    public static Map<String, Participant> getParticipants(@NonNull String realm) {
        Map<String, Participant> participants = Participant.getParticipants(realm);
        Map<String, ParticipantExit> exitedParticipants = ParticipantExit.getExitedParticipants(realm, false);
        for (Map.Entry<String, ParticipantExit> entry: exitedParticipants.entrySet()) {
            Participant participant = new Participant();
            String ddpParticipantId = entry.getKey();
            participant.setDdpParticipantId(ddpParticipantId);
            participant.setExitDate(entry.getValue().getExitDate());
            participants.putIfAbsent(ddpParticipantId, participant);
        }
        return participants;
    }
}



