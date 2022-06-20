package org.broadinstitute.dsm.model.elastic.migration;

import java.util.Map;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantExit;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.generate.Generator;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class ParticipantMigrator extends BaseSingleMigrator implements Exportable, Generator {

    public ParticipantMigrator(String index, String realm) {
        super(index, realm, ESObjectConstants.PARTICIPANT);
    }

    public ParticipantMigrator(String index, String realm, String object) {
        super(index, realm, object);
    }

    @Override
    protected Map<String, Object> getDataByRealm() {
        Map<String, Participant> participants = getParticipantsByRealm(realm);
        Map<String, ParticipantExit> exitedParticipants = getExitedParticipantsByRealm(realm);
        for (Map.Entry<String, ParticipantExit> entry: exitedParticipants.entrySet()) {
            Participant participant = new Participant();
            String ddpParticipantId = entry.getKey();
            participant.setDdpParticipantId(ddpParticipantId);
            participant.setExitDate(entry.getValue().getExitDate());
            participants.putIfAbsent(ddpParticipantId, participant);
        }
        return (Map) participants;
    }

    protected Map<String, ParticipantExit> getExitedParticipantsByRealm(String realm) {
        return ParticipantExit.getExitedParticipants(realm, false);
    }

    protected Map<String, Participant> getParticipantsByRealm(String realm) {
        return Participant.getParticipants(realm);
    }

}



