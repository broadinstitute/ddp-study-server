
package org.broadinstitute.dsm.util.export;

import org.broadinstitute.dsm.db.NewOsteoParticipant;
import org.broadinstitute.dsm.db.Participant;

public class NewOsteoParticipantExporter extends DefaultParticipantExporter {

    public NewOsteoParticipantExporter(ParticipantExportPayload payload) {
        super(payload);
    }

    @Override
    public Participant buildParticipantFromPayload(ParticipantExportPayload payload) {
        return new NewOsteoParticipant(
                payload.getParticipantId(),
                payload.getDdpParticipantId(),
                Integer.parseInt(payload.getInstanceId())
        );
    }

}

