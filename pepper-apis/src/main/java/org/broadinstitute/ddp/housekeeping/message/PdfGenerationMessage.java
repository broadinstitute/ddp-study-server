package org.broadinstitute.ddp.housekeeping.message;

import com.google.gson.annotations.SerializedName;

public class PdfGenerationMessage implements HousekeepingMessage {
    @SerializedName("participantGuid")
    private String participantGuid;

    @SerializedName("studyGuid")
    private String studyGuid;

    @SerializedName("eventConfigId")
    private long eventConfigurationId;

    @SerializedName("pdfDocumentConfigurationId")
    private long pdfDocumentConfigurationId;


    public PdfGenerationMessage(String participantGuid,
                                String studyGuid,
                                long eventConfigurationId,
                                long pdfDocumentConfigurationId) {
        this.participantGuid = participantGuid;
        this.studyGuid = studyGuid;
        this.eventConfigurationId = eventConfigurationId;
        this.pdfDocumentConfigurationId = pdfDocumentConfigurationId;
    }

    @Override
    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public long getPdfDocumentConfigurationId() {
        return pdfDocumentConfigurationId;
    }
}
