package org.broadinstitute.ddp.model.activity.instance;

import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;


public final class PhysicianComponent extends PhysicianInstitutionComponent {

    public PhysicianComponent(boolean allowMultiple,
                              String addButtonText,
                              String titleText,
                              String subtitleText,
                              InstitutionType institutionType,
                              boolean showFields,
                              boolean required,
                              boolean hideNumber) {
        super(ComponentType.PHYSICIAN,
                allowMultiple,
                addButtonText,
                titleText,
                subtitleText,
                institutionType,
                showFields,
                required,
                hideNumber);
    }
}
