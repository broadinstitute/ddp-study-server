package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class InstitutionPhysicianComponentDto {

    private InstitutionType institutionType;
    private Long titleTemplateId;
    private Long subtitleTemplateId;
    private Long buttonTemplateId;
    private boolean allowMultiple;
    private boolean showFields;
    private boolean required;

    @JdbiConstructor
    public InstitutionPhysicianComponentDto(@ColumnName("institution_type") InstitutionType institutionType,
                                            @ColumnName("title_template_id") Long titleTemplateId,
                                            @ColumnName("subtitle_template_id") Long subtitleTemplateId,
                                            @ColumnName("add_button_template_id") Long buttonTemplateId,
                                            @ColumnName("allow_multiple") boolean allowMultiple,
                                            @ColumnName("show_fields_initially") boolean showFields,
                                            @ColumnName("required") boolean required) {
        this.institutionType = institutionType;
        this.titleTemplateId = titleTemplateId;
        this.subtitleTemplateId = subtitleTemplateId;
        this.buttonTemplateId = buttonTemplateId;
        this.allowMultiple = allowMultiple;
        this.showFields = showFields;
        this.required = required;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }

    public Long getTitleTemplateId() {
        return titleTemplateId;
    }

    public Long getSubtitleTemplateId() {
        return subtitleTemplateId;
    }

    public Long getButtonTemplateId() {
        return buttonTemplateId;
    }

    public boolean getAllowMultiple() {
        return allowMultiple;
    }

    public boolean showFields() {
        return showFields;
    }

    public boolean isRequired() {
        return required;
    }
}
