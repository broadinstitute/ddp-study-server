package org.broadinstitute.ddp.model.activity.definition;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

public abstract class PhysicianInstitutionComponentDef extends ComponentBlockDef {

    private boolean allowMultiple;

    @Valid
    private Template addButtonTemplate;

    @Valid
    private Template titleTemplate;

    @Valid
    private Template subtitleTemplate;

    @NotNull
    private InstitutionType institutionType;

    private boolean showFields;

    private boolean required;

    protected PhysicianInstitutionComponentDef(ComponentType physicianOrInstitution,
                                               boolean allowMultiple,
                                               Template addButtonTemplate,
                                               Template titleTemplate,
                                               Template subtitleTemplate,
                                               InstitutionType institutionType,
                                               boolean showFields,
                                               boolean required) {
        super(physicianOrInstitution);
        if (physicianOrInstitution != ComponentType.PHYSICIAN && physicianOrInstitution != ComponentType.INSTITUTION) {
            throw new IllegalArgumentException("Physician/Institution component must be either "
                    + ComponentType.PHYSICIAN + " or " + ComponentType.INSTITUTION);
        }
        this.allowMultiple = allowMultiple;
        this.addButtonTemplate = addButtonTemplate;
        this.titleTemplate = titleTemplate;
        this.subtitleTemplate = subtitleTemplate;
        this.institutionType = institutionType;
        this.showFields = showFields;
        this.required = required;
    }

    public boolean allowMultiple() {
        return allowMultiple;
    }

    public Template getAddButtonTemplate() {
        return addButtonTemplate;
    }

    public Template getTitleTemplate() {
        return titleTemplate;
    }

    public Template getSubtitleTemplate() {
        return subtitleTemplate;
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }

    public boolean showFields() {
        return showFields;
    }

    public boolean isRequired() {
        return required;
    }
}
