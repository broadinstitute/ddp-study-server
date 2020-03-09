package org.broadinstitute.ddp.model.activity.instance;

import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.model.activity.types.ComponentType;

public final class PhysicianComponent extends PhysicianInstitutionComponent {

    public PhysicianComponent(
            InstitutionPhysicianComponentDto instDto,
            boolean shouldHideNumber
    ) {
        super(ComponentType.PHYSICIAN, instDto, shouldHideNumber);
    }
}
