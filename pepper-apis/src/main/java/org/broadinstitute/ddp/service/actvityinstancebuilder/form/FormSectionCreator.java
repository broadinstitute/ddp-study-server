package org.broadinstitute.ddp.service.actvityinstancebuilder.form;


import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

/**
 * Creates {@link FormSection}
 */
public class FormSectionCreator {

    public FormSection createSection(AIBuilderContext ctx, FormSectionDef formSectionDef) {
        if (formSectionDef != null) {
            var formSection = new FormSection(ctx.getAIBuilderFactory().getTemplateRenderHelper().addTemplate(
                    ctx, formSectionDef.getNameTemplate()));
            addChildren(ctx, formSection, formSectionDef);
            return formSection;
        }
        return null;
    }

    /**
     * Find and build all icons of a given section.<br>
     * Find and build all blocks for given section, respecting the display order of blocks within each section. If
     * there's no blocks for a section, it will be an empty list.<br>
     * Blocks with deprecated questions are null-ed out and should be filtered out by
     * caller. Prefer to exclude deprecated questions unless needed, as in the case of data export.
     */
    private void addChildren(AIBuilderContext ctx, FormSection formSection, FormSectionDef formSectionDef) {
        formSectionDef.getIcons().forEach(i -> formSection.getIcons().add(
                ctx.getAIBuilderFactory().getSectionIconCreator().createSectionIcon(i)));
        formSectionDef.getBlocks().forEach(b -> {
            var block = ctx.getAIBuilderFactory().getFormBlockCreator().createBlock(ctx, b);
            if (block != null) {
                formSection.getBlocks().add(block);
            }
        });
    }
}
