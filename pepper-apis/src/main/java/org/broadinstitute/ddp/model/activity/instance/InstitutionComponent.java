package org.broadinstitute.ddp.model.activity.instance;

import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;


public final class InstitutionComponent extends PhysicianInstitutionComponent {

    public InstitutionComponent(boolean allowMultiple,
                                String addButtonText,
                                String titleText,
                                String subtitleText,
                                InstitutionType institutionType,
                                boolean showFields,
                                boolean hideNumber) {
        super(ComponentType.INSTITUTION,
                allowMultiple,
                addButtonText,
                titleText,
                subtitleText,
                institutionType,
                showFields,
                hideNumber);
    }
}
