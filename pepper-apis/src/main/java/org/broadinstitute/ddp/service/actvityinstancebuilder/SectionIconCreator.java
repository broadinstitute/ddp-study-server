package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.model.activity.definition.SectionIcon;

/**
 * Creates {@link SectionIcon}
 */
public class SectionIconCreator extends ElementCreator {

    public SectionIconCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public SectionIcon createSectionIcon(SectionIcon sectionIconDef) {
        SectionIcon sectionIcon = constructSectionIcon(sectionIconDef);
        return sectionIcon;
    }

    private SectionIcon constructSectionIcon(SectionIcon sectionIconDef) {
        SectionIcon sectionIcon = new SectionIcon(
                sectionIconDef.getIconId(),
                sectionIconDef.getSectionId(),
                sectionIconDef.getState(),
                sectionIconDef.getHeight(),
                sectionIconDef.getWidth()
        );
        return sectionIcon;
    }
}
