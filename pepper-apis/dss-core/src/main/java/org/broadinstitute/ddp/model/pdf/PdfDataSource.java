package org.broadinstitute.ddp.model.pdf;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PdfDataSource {

    private long id;
    private PdfDataSourceType type;

    @JdbiConstructor
    public PdfDataSource(@ColumnName("pdf_data_source_id") long id,
                         @ColumnName("pdf_data_source_type") PdfDataSourceType type) {
        this.id = id;
        this.type = type;
    }

    public PdfDataSource(PdfDataSourceType type) {
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public PdfDataSourceType getType() {
        return type;
    }
}
