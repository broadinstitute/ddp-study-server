package org.broadinstitute.ddp.model.pdf;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * A pdf template, used to compose a larger pdf document, and part of the pdf configuration. Includes the raw pdf file to render from, and
 * the field substitutions.
 */
public abstract class PdfTemplate {

    private long id;
    private PdfTemplateType type;
    private byte[] rawBytes;

    PdfTemplate(long id, PdfTemplateType type, byte[] rawBytes) {
        this.id = id;
        this.type = type;
        this.rawBytes = rawBytes;
    }

    PdfTemplate(PdfTemplateType type, byte[] rawBytes) {
        this.type = type;
        this.rawBytes = rawBytes;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public PdfTemplateType getType() {
        return type;
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    /**
     * Returns the template for reading. Each time called, a new stream is created, backed by the underlying bytes.
     */
    public InputStream asByteStream() {
        return new ByteArrayInputStream(rawBytes);
    }
}
