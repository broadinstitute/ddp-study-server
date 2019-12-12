package org.broadinstitute.ddp.db.dto;

public class QueuedPdfGenerationDto extends QueuedEventDto {

    private long pdfDocumentConfigurationId;

    public QueuedPdfGenerationDto(QueuedEventDto pendingEvent,
                                  long pdfDocumentConfigurationId) {
        super(pendingEvent.getEventConfigurationId(),
                pendingEvent.getQueuedEventId(),
                pendingEvent.getParticipantGuid(),
                pendingEvent.getParticipantHruid(),
                pendingEvent.getActionType(),
                pendingEvent.getHousekeepingVersion(),
                pendingEvent.getMaxOccurrencesPerUser(),
                pendingEvent.getPubSubTopic(),
                pendingEvent.getPrecondition(),
                pendingEvent.getCancelCondition(),
                pendingEvent.getStudyGuid());
        this.pdfDocumentConfigurationId = pdfDocumentConfigurationId;
    }

    public long getPdfDocumentConfigurationId() {
        return pdfDocumentConfigurationId;
    }
}
