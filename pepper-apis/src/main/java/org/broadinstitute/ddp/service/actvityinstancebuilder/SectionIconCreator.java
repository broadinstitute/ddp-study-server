package org.broadinstitute.ddp.service.actvityinstancebuilder;


import org.broadinstitute.ddp.model.activity.definition.SectionIcon;

/**
 * Creates {@link SectionIcon}
 */
public class SectionIconCreator {

    public SectionIcon createSectionIcon(SectionIcon sectionIconDef) {
        return new SectionIcon(
                sectionIconDef.getIconId(),
                sectionIconDef.getSectionId(),
                sectionIconDef.getState(),
                sectionIconDef.getHeight(),
                sectionIconDef.getWidth()
        );
    }
}
