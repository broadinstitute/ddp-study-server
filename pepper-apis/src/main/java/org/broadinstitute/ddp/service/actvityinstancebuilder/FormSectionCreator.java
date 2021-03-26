package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.FormSection;

/**
 * Creates {@link FormSection}
 */
public class FormSectionCreator extends AbstractCreator {

    public FormSectionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public FormSection createSection(FormSectionDef formSectionDef) {
        if (formSectionDef != null) {
            var formSection = new FormSection(renderTemplateIfDefined(formSectionDef.getNameTemplate()));
            addChildren(formSection, formSectionDef);
            return formSection;
        }
        return null;
    }

    private void addChildren(FormSection formSection, FormSectionDef formSectionDef) {
        formSectionDef.getIcons().forEach(i -> formSection.getIcons().add(context.getSectionIconCreator().createSectionIcon(i)));
        formSectionDef.getBlocks().forEach(b -> formSection.getBlocks().add(context.getFormBlockCreator().createBlock(b)));
    }
}
