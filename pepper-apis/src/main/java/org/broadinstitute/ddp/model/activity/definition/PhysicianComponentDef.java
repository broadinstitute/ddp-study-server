package org.broadinstitute.ddp.model.activity.definition;

import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

public final class PhysicianComponentDef extends PhysicianInstitutionComponentDef {

    public PhysicianComponentDef(boolean allowMultiple,
                                 Template addButtonTemplate,
                                 Template titleTemplate,
                                 Template subtitleTemplate,
                                 InstitutionType institutionType,
                                 boolean showFields) {
        super(ComponentType.PHYSICIAN, allowMultiple, addButtonTemplate, titleTemplate, subtitleTemplate, institutionType, showFields);
    }
}
