package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.db.dto.ComponentDto;
import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.InstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.instance.InstitutionComponent;
import org.broadinstitute.ddp.model.activity.instance.PhysicianComponent;
import org.broadinstitute.ddp.model.activity.instance.PhysicianInstitutionComponent;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;

/**
 * Creates {@link InstitutionComponent}, {@link PhysicianComponent}
 * (base class is {@link PhysicianInstitutionComponent} )
 */
class PhysicianInstitutionComponentBlockCreator extends ElementCreator {

    public PhysicianInstitutionComponentBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public PhysicianComponent createPhysicianComponent(ComponentBlockDef componentBlockDef) {
        PhysicianComponent physicianComponent = new PhysicianComponent(
                createInstitutionPhysicianComponentDto((PhysicianComponentDef)componentBlockDef),
                componentBlockDef.shouldHideNumber()
        );
        render(physicianComponent, (PhysicianComponentDef)componentBlockDef);
        return physicianComponent;
    }

    public InstitutionComponent createInstitutionComponent(ComponentBlockDef componentBlockDef) {
        InstitutionComponent institutionComponent = new InstitutionComponent(
                createInstitutionPhysicianComponentDto((InstitutionComponentDef)componentBlockDef),
                componentBlockDef.shouldHideNumber()
        );
        render(institutionComponent, (InstitutionComponentDef)componentBlockDef);
        return institutionComponent;
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
                physicianInstitutionComponentDef.getTitleTemplate() != null
                        ? physicianInstitutionComponentDef.getTitleTemplate().getTemplateId() : null,
                physicianInstitutionComponentDef.getSubtitleTemplate() != null
                        ? physicianInstitutionComponentDef.getSubtitleTemplate().getTemplateId() : null,
                physicianInstitutionComponentDef.getAddButtonTemplate() != null
                        ? physicianInstitutionComponentDef.getAddButtonTemplate().getTemplateId() : null,
                physicianInstitutionComponentDef.allowMultiple(),
                physicianInstitutionComponentDef.showFields(),
                physicianInstitutionComponentDef.isRequired()
        );
    }

    private void render(PhysicianInstitutionComponent physicianInstitutionComponent,
                        PhysicianInstitutionComponentDef physicianInstitutionComponentDef) {
        renderTemplate(physicianInstitutionComponentDef.getTitleTemplate(),
                physicianInstitutionComponentDef.getTitleTemplate().getTemplateId(), physicianInstitutionComponent, context);
        renderTemplate(physicianInstitutionComponentDef.getSubtitleTemplate(),
                physicianInstitutionComponentDef.getSubtitleTemplate().getTemplateId(), physicianInstitutionComponent, context);
        renderTemplate(physicianInstitutionComponentDef.getAddButtonTemplate(),
                physicianInstitutionComponentDef.getAddButtonTemplate().getTemplateId(), physicianInstitutionComponent, context);
    }
}
