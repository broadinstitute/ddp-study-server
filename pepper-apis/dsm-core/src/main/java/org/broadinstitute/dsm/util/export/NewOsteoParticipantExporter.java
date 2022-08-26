package org.broadinstitute.dsm.util.export;

public class NewOsteoParticipantExporter extends ParticipantExporter {


        public NewOsteoParticipantExporter(ParticipantExportPayload payload) {
            super(payload);
        }

        @Override
        public void export() {

            NewOsteoParticipant newOsteoParticipant = new NewOsteoParticipant(
                    participantExportPayload.getParticipantId(),
                    participantExportPayload.getDdpParticipantId(),
                    Integer.parseInt(participantExportPayload.getInstanceId()));

            UpsertPainlessFacade.of(DBConstants.DDP_PARTICIPANT_ALIAS, newOsteoParticipant,
                    participantExportPayload.getDdpInstanceDto(),
                    ESObjectConstants.PARTICIPANT_ID, ESObjectConstants.DOC_ID, participantExportPayload.getDdpParticipantId(),
                    new AddToSingleScriptBuilder()).export();

        }
    }