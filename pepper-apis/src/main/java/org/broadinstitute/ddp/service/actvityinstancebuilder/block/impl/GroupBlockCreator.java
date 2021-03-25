package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;


import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.FormBlockCreator;


/**
 * Creates {@link GroupBlock}
 */
public class GroupBlockCreator extends ElementCreator {

    public GroupBlockCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public GroupBlock createGroupBlock(GroupBlockDef groupBlockDef) {
        var groupBlock = constructComponentBlock(groupBlockDef);
        addChildren(groupBlock, groupBlockDef);
        return groupBlock;
    }

    private GroupBlock constructComponentBlock(GroupBlockDef groupBlockDef) {
        return new GroupBlock(
                groupBlockDef.getListStyleHint(),
                groupBlockDef.getPresentationHint(),
                renderTemplateIfDefined(groupBlockDef.getTitleTemplate())
        );
    }

    private void addChildren(GroupBlock groupBlock, GroupBlockDef groupBlockDef) {
        if (groupBlockDef.getNested() != null) {
            var blockCreator = new FormBlockCreator(context);
            groupBlockDef.getNested().forEach(b ->
                    groupBlock.getNested().add(blockCreator.createBlock(b)));
        }
    }
}
