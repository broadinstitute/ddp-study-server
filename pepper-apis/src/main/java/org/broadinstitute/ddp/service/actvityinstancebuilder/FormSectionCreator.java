package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.FormBlockCreator;

/**
 * Creates {@link FormSection}
 */
public class FormSectionCreator extends ElementCreator {

    public FormSectionCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public FormSection createSection(FormSectionDef formSectionDef) {
        if (formSectionDef != null) {
            FormSection formSection = constructFormSection(formSectionDef);
            addChildren(formSection, formSectionDef);
            applyRenderedTemplates(formSection);
            return formSection;
        }
        return null;
    }

    private FormSection constructFormSection(FormSectionDef formSectionDef) {
        return new FormSection(getTemplateId(formSectionDef.getNameTemplate()));
    }

    private void addChildren(FormSection formSection, FormSectionDef formSectionDef) {
        SectionIconCreator sectionIconCreator = new SectionIconCreator(context);
        formSectionDef.getIcons().forEach(i -> formSection.getIcons().add(sectionIconCreator.createSectionIcon(i)));

        FormBlockCreator formBlockCreator = new FormBlockCreator(context);
        formSectionDef.getBlocks().forEach(b -> formSection.getBlocks().add(formBlockCreator.createBlock(b)));
    }
}
