package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.FormBlockCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;

/**
 * Creates {@link GroupBlock}
 */
public class GroupBlockCreator extends ElementCreator {

    public GroupBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public GroupBlock createGroupBlock(GroupBlockDef groupBlockDef) {
        GroupBlock groupBlock = constructComponentBlock(groupBlockDef);
        addChildren(groupBlock, groupBlockDef);
        render(groupBlock, groupBlockDef);
        return groupBlock;
    }

    private GroupBlock constructComponentBlock(GroupBlockDef groupBlockDef) {
        return new GroupBlock(
                groupBlockDef.getListStyleHint(),
                groupBlockDef.getPresentationHint(),
                groupBlockDef.getTitleTemplate() != null ? groupBlockDef.getTitleTemplate().getTemplateId() : null
        );
    }

    private void addChildren(GroupBlock groupBlock, GroupBlockDef groupBlockDef) {
        if (groupBlockDef.getNested() != null) {
            groupBlockDef.getNested().forEach(b ->
                    groupBlock.getNested().add(new FormBlockCreator(context).createBlock(b)));
        }
    }

    private void render(GroupBlock groupBlock, GroupBlockDef groupBlockDef) {
        renderTemplate(groupBlockDef.getTitleTemplate(), groupBlock.getTitleTemplateId(), groupBlock, context);
    }
}
