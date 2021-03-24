package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;


import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.FormBlockCreator;


/**
 * Creates {@link GroupBlock}
 */
public class GroupBlockCreator extends ElementCreator {

    public GroupBlockCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public GroupBlock createGroupBlock(GroupBlockDef groupBlockDef) {
        GroupBlock groupBlock = constructComponentBlock(groupBlockDef);
        addChildren(groupBlock, groupBlockDef);
        return groupBlock;
    }

    private GroupBlock constructComponentBlock(GroupBlockDef groupBlockDef) {
        return new GroupBlock(
                groupBlockDef.getListStyleHint(),
                groupBlockDef.getPresentationHint(),
                getTemplateId(groupBlockDef.getTitleTemplate())
        );
    }

    private void addChildren(GroupBlock groupBlock, GroupBlockDef groupBlockDef) {
        if (groupBlockDef.getNested() != null) {
            groupBlockDef.getNested().forEach(b ->
                    groupBlock.getNested().add(new FormBlockCreator(context).createBlock(b)));
        }
    }
}
