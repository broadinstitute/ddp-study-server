package org.broadinstitute.ddp.model.event;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PdfAttachment {

    private long pdfConfigId;
    private boolean generateIfMissing;

    @JdbiConstructor
    public PdfAttachment(@ColumnName("pdf_document_configuration_id") long pdfConfigId,
                         @ColumnName("generate_if_missing") boolean generateIfMissing) {
        this.pdfConfigId = pdfConfigId;
        this.generateIfMissing = generateIfMissing;
    }

    public long getPdfConfigId() {
        return pdfConfigId;
    }

    public boolean shouldGenerateIfMissing() {
        return generateIfMissing;
    }
}
