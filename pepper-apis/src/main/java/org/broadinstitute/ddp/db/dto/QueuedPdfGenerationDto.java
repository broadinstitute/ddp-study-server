package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class QueuedPdfGenerationDto extends QueuedEventDto {

    private long pdfDocumentConfigurationId;

    @JdbiConstructor
    public QueuedPdfGenerationDto(
            @Nested QueuedEventDto pendingEvent,
            @ColumnName("pdf_document_configuration_id") long pdfDocumentConfigurationId) {
        super(pendingEvent);
        this.pdfDocumentConfigurationId = pdfDocumentConfigurationId;
    }

    public long getPdfDocumentConfigurationId() {
        return pdfDocumentConfigurationId;
    }
}
