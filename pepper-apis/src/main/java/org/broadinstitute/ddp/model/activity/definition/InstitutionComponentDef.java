package org.broadinstitute.ddp.model.activity.definition;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

public final class InstitutionComponentDef extends PhysicianInstitutionComponentDef {

    public InstitutionComponentDef(boolean allowMultiple,
                                   Template addButtonTemplate,
                                   Template titleTemplate,
                                   Template subtitleTemplate,
                                   InstitutionType institutionType,
                                   boolean showFields,
                                   boolean required) {
        super(ComponentType.INSTITUTION, allowMultiple, addButtonTemplate, titleTemplate, subtitleTemplate, institutionType,
                showFields, required);
    }
}
