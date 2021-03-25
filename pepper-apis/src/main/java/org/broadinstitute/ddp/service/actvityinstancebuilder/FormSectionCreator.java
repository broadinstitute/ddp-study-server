package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.FormBlockCreator;

/**
 * Creates {@link FormSection}
 */
public class FormSectionCreator extends ElementCreator {

    public FormSectionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public FormSection createSection(FormSectionDef formSectionDef) {
        if (formSectionDef != null) {
            var formSection = constructFormSection(formSectionDef);
            addChildren(formSection, formSectionDef);
            return formSection;
        }
        return null;
    }

    private FormSection constructFormSection(FormSectionDef formSectionDef) {
        return new FormSection(renderTemplateIfDefined(formSectionDef.getNameTemplate()));
    }

    private void addChildren(FormSection formSection, FormSectionDef formSectionDef) {
        var sectionIconCreator = new SectionIconCreator(context);
        formSectionDef.getIcons().forEach(i -> formSection.getIcons().add(sectionIconCreator.createSectionIcon(i)));

        var formBlockCreator = new FormBlockCreator(context);
        formSectionDef.getBlocks().forEach(b -> formSection.getBlocks().add(formBlockCreator.createBlock(b)));
    }
}
