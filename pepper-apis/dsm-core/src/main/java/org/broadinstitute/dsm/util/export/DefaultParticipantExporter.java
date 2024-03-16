
package org.broadinstitute.dsm.util.export;

import java.util.function.Function;

import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.model.elastic.export.Exportable;
import org.broadinstitute.dsm.model.elastic.export.painless.AddToSingleScriptBuilder;
import org.broadinstitute.dsm.model.elastic.export.painless.UpsertPainlessFacade;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class DefaultParticipantExporter implements Exportable {

    protected final ParticipantExportPayload participantExportPayload;

    protected final Function<ParticipantExportPayload, Participant> buildParticipant = this::buildParticipantFromPayload;
    protected final Function<Participant, UpsertPainlessFacade> buildUpsertPainless  = this::buildUpsertPainlessFacadeFromParticipant;

    public DefaultParticipantExporter(ParticipantExportPayload participantExportPayload) {
        this.participantExportPayload = participantExportPayload;
    }

    @Override
    public void export() {
        buildParticipant
                .andThen(buildUpsertPainless)
                .apply(participantExportPayload)
                .export();
    }

    private UpsertPainlessFacade buildUpsertPainlessFacadeFromParticipant(Participant participant) {
        return UpsertPainlessFacade.of(DBConstants.DDP_PARTICIPANT_ALIAS, participant, participantExportPayload.getDdpInstanceDto(),
                ESObjectConstants.PARTICIPANT_ID, ESObjectConstants.DOC_ID, participantExportPayload.getDdpParticipantId(),
                new AddToSingleScriptBuilder());
    }

    public Participant buildParticipantFromPayload(ParticipantExportPayload payload) {
        return new Participant(payload.getParticipantId(),
                payload.getDdpParticipantId(),
                payload.getInstanceId()
        );
    }

}
