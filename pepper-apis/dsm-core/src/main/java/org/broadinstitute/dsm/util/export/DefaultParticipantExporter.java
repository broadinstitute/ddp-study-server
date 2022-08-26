
package org.broadinstitute.dsm.util.export;

import java.util.function.Function;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.model.elastic.export.painless.AddToSingleScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class PlainParticipantExporter {

    protected final ParticipantExportPayload participantExportPayload;

    protected Function<ParticipantExportPayload, Participant> buildParticipant      = this::buildParticipantFromPayload;
    protected Function<Participant, UpsertPainlessFacade> buildUpsertPainlessFacade = this::buildUpsertPainlessFacadeFromParticipant;

    public PlainParticipantExporter(ParticipantExportPayload participantExportPayload) {
        this.participantExportPayload = participantExportPayload;
    }

    public void export() {
        buildParticipant
                .andThen(buildUpsertPainlessFacade)
                .apply(participantExportPayload)
                .export();
    }

    private UpsertPainlessFacade buildUpsertPainlessFacadeFromParticipant(Participant participant) {
        return UpsertPainlessFacade.of(DBConstants.DDP_PARTICIPANT_ALIAS, participant, participantExportPayload.getDdpInstanceDto(),
                ESObjectConstants.PARTICIPANT_ID, ESObjectConstants.DOC_ID, participantExportPayload.getDdpParticipantId(),
                new AddToSingleScriptBuilder());
    }

    protected Participant buildParticipantFromPayload(ParticipantExportPayload payload) {
        return new Participant(payload.getParticipantId(),
                payload.getDdpParticipantId(),
                Integer.parseInt(payload.getInstanceId())
        );
    }

}
