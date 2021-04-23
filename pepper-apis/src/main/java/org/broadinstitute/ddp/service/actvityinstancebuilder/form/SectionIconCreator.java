package org.broadinstitute.ddp.service.actvityinstancebuilder.form;


import org.broadinstitute.ddp.model.activity.definition.SectionIcon;

/**
 * Creates {@link SectionIcon}
 */
public class SectionIconCreator {

    public SectionIcon createSectionIcon(SectionIcon sectionIconDef) {
        SectionIcon sectionIcon = new SectionIcon(
                sectionIconDef.getIconId(),
                sectionIconDef.getSectionId(),
                sectionIconDef.getState(),
                sectionIconDef.getHeight(),
                sectionIconDef.getWidth()
        );
        if (sectionIconDef.getSources().size() > 0) {
            sectionIcon.getSources().putAll(sectionIconDef.getSources());
        }
        return sectionIcon;
    }
}
