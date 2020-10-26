package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class FormSectionDto {

    private long id;
    private String sectionCode;
    private Long nameTemplateId;

    @JdbiConstructor
    public FormSectionDto(
            @ColumnName("form_section_id") long id,
            @ColumnName("form_section_code") String sectionCode,
            @ColumnName("name_template_id") Long nameTemplateId) {
        this.id = id;
        this.sectionCode = sectionCode;
        this.nameTemplateId = nameTemplateId;
    }

    public long getId() {
        return id;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public Long getNameTemplateId() {
        return nameTemplateId;
    }
}
