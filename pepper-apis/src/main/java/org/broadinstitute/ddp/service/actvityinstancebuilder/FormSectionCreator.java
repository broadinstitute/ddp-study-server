package org.broadinstitute.ddp.service.actvityinstancebuilder;


import static org.broadinstitute.ddp.service.actvityinstancebuilder.util.TemplateHandler.addAndRenderTemplate;

import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.FormSection;

/**
 * Creates {@link FormSection}
 */
public class FormSectionCreator {

    public FormSection createSection(AIBuilderContext ctx, FormSectionDef formSectionDef) {
        if (formSectionDef != null) {
            var formSection = new FormSection(addAndRenderTemplate(ctx, formSectionDef.getNameTemplate()));
            addChildren(ctx, formSection, formSectionDef);
            return formSection;
        }
        return null;
    }

    private void addChildren(AIBuilderContext ctx, FormSection formSection, FormSectionDef formSectionDef) {
        formSectionDef.getIcons().forEach(i -> formSection.getIcons().add(ctx.creators().getSectionIconCreator().createSectionIcon(i)));
        formSectionDef.getBlocks().forEach(b -> {
            var block = ctx.creators().getFormBlockCreator().createBlock(ctx, b);
            if (block != null) {
                formSection.getBlocks().add(block);
            }
        });
    }
}
