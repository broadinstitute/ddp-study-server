package org.broadinstitute.ddp.db.dto;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class QueuedPdfGenerationDto extends QueuedEventDto {
    long pdfDocumentConfigurationId;

    @JdbiConstructor
    public QueuedPdfGenerationDto(
            @Nested QueuedEventDto pendingEvent,
            @ColumnName("pdf_document_configuration_id") long pdfDocumentConfigurationId) {
        super(pendingEvent);
        this.pdfDocumentConfigurationId = pdfDocumentConfigurationId;
    }
}
