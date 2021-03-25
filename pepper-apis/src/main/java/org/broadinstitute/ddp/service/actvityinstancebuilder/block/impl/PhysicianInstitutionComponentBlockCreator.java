package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;


import org.broadinstitute.ddp.db.dto.ComponentDto;
import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.InstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.instance.InstitutionComponent;
import org.broadinstitute.ddp.model.activity.instance.PhysicianComponent;
import org.broadinstitute.ddp.model.activity.instance.PhysicianInstitutionComponent;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;


/**
 * Creates {@link InstitutionComponent}, {@link PhysicianComponent}
 * (base class is {@link PhysicianInstitutionComponent} )
 */
class PhysicianInstitutionComponentBlockCreator extends ElementCreator {

    public PhysicianInstitutionComponentBlockCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public PhysicianComponent createPhysicianComponent(ComponentBlockDef componentBlockDef) {
        return new PhysicianComponent(
                createInstitutionPhysicianComponentDto((PhysicianComponentDef)componentBlockDef),
                componentBlockDef.shouldHideNumber()
        );
    }

    public InstitutionComponent createInstitutionComponent(ComponentBlockDef componentBlockDef) {
        return new InstitutionComponent(
                createInstitutionPhysicianComponentDto((InstitutionComponentDef)componentBlockDef),
                componentBlockDef.shouldHideNumber()
        );
    }

    private InstitutionPhysicianComponentDto createInstitutionPhysicianComponentDto(
            PhysicianInstitutionComponentDef physicianInstitutionComponentDef) {
        return new InstitutionPhysicianComponentDto(
                new ComponentDto(
                        physicianInstitutionComponentDef.getBlockId(),
                        physicianInstitutionComponentDef.getComponentType(),
                        physicianInstitutionComponentDef.shouldHideNumber(),
                        physicianInstitutionComponentDef.getRevisionId()
                ),
                physicianInstitutionComponentDef.getInstitutionType(),
                renderTemplateIfDefined(physicianInstitutionComponentDef.getTitleTemplate()),
                renderTemplateIfDefined(physicianInstitutionComponentDef.getSubtitleTemplate()),
                renderTemplateIfDefined(physicianInstitutionComponentDef.getAddButtonTemplate()),
                physicianInstitutionComponentDef.allowMultiple(),
                physicianInstitutionComponentDef.showFields(),
                physicianInstitutionComponentDef.isRequired()
        );
    }
}
