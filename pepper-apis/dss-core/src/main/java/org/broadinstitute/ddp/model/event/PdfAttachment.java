package org.broadinstitute.ddp.model.event;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PdfAttachment {

    private long pdfConfigId;
    private boolean alwaysGenerate;

    @JdbiConstructor
    public PdfAttachment(@ColumnName("pdf_document_configuration_id") long pdfConfigId,
                         @ColumnName("always_generate") boolean alwaysGenerate) {
        this.pdfConfigId = pdfConfigId;
        this.alwaysGenerate = alwaysGenerate;
    }

    public long getPdfConfigId() {
        return pdfConfigId;
    }

    public boolean shouldAlwaysGenerate() {
        return alwaysGenerate;
    }
}
