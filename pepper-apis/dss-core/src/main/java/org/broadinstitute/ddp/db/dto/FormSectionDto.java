package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class FormSectionDto {
    @ColumnName("form_section_id")
    long id;

    @ColumnName("form_section_code")
    String sectionCode;

    @ColumnName("name_template_id")
    Long nameTemplateId;
}
