package org.broadinstitute.ddp.service.actvityinstanceassembler;


import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.FormBlockCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;

/**
 * Creates {@link FormSection}
 */
public class FormSectionCreator extends ElementCreator {

    public FormSectionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public FormSection createSection(FormSectionDef formSectionDef) {
        if (formSectionDef != null) {
            FormSection formSection = constructFormSection(formSectionDef);
            addChildren(formSection, formSectionDef);
            render(formSection, formSectionDef);
            return formSection;
        }
        return null;
    }

    private FormSection constructFormSection(FormSectionDef formSectionDef) {
        FormSection formSection = new FormSection(
                formSectionDef.getNameTemplate() != null ? formSectionDef.getNameTemplate().getTemplateId() : null
        );
        return formSection;
    }

    private void addChildren(FormSection formSection, FormSectionDef formSectionDef) {
        SectionIconCreator sectionIconCreator = new SectionIconCreator(context);
        formSectionDef.getIcons().forEach(i -> formSection.getIcons().add(sectionIconCreator.createSectionIcon(i)));

        FormBlockCreator formBlockCreator = new FormBlockCreator(context);
        formSectionDef.getBlocks().forEach(b -> formSection.getBlocks().add(formBlockCreator.createBlock(b)));
    }

    private void render(FormSection formSection, FormSectionDef formSectionDef) {
        renderTemplate(formSectionDef.getNameTemplate(), formSection, context);
    }
}
