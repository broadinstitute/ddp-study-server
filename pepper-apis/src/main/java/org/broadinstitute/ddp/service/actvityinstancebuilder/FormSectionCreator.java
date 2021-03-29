package org.broadinstitute.ddp.service.actvityinstancebuilder;


import static org.broadinstitute.ddp.service.actvityinstancebuilder.TemplateHandler.addAndRenderTemplate;

import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.FormSection;

/**
 * Creates {@link FormSection}
 */
public class FormSectionCreator {

    public FormSection createSection(Context ctx, FormSectionDef formSectionDef) {
        if (formSectionDef != null) {
            var formSection = new FormSection(addAndRenderTemplate(ctx, formSectionDef.getNameTemplate()));
            addChildren(ctx, formSection, formSectionDef);
            return formSection;
        }
        return null;
    }

    private void addChildren(Context ctx, FormSection formSection, FormSectionDef formSectionDef) {
        formSectionDef.getIcons().forEach(i -> formSection.getIcons().add(ctx.creators().getSectionIconCreator().createSectionIcon(i)));
        formSectionDef.getBlocks().forEach(b -> formSection.getBlocks().add(ctx.creators().getFormBlockCreator().createBlock(ctx, b)));
    }
}
