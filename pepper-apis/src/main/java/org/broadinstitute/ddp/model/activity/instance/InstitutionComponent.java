package org.broadinstitute.ddp.model.activity.instance;

import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.model.activity.types.ComponentType;

public final class InstitutionComponent extends PhysicianInstitutionComponent {

    public InstitutionComponent(
            InstitutionPhysicianComponentDto instDto,
            boolean shouldHideNumber
    ) {
        super(ComponentType.INSTITUTION, instDto, shouldHideNumber);
    }
}
