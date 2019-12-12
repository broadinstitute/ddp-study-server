package org.broadinstitute.ddp.db.dto.pdf;

import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PdfTemplateDto {

    private long id;
    private byte[] blob;
    private PdfTemplateType type;

    @JdbiConstructor
    public PdfTemplateDto(@ColumnName("template_id") long id,
                          @ColumnName("template_blob") byte[] blob,
                          @ColumnName("template_type") PdfTemplateType type) {
        this.id = id;
        this.blob = blob;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public byte[] getBlob() {
        return blob;
    }

    public PdfTemplateType getType() {
        return type;
    }
}
