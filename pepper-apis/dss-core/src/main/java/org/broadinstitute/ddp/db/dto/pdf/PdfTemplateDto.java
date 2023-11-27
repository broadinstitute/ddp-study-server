package org.broadinstitute.ddp.db.dto.pdf;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.pdf.PdfTemplateType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class PdfTemplateDto {
    @ColumnName("template_id")
    long id;
    
    @ColumnName("template_blob")
    byte[] blob;

    @ColumnName("language_code_id")
    long languageCodeId;

    @ColumnName("template_type")
    PdfTemplateType type;
}
