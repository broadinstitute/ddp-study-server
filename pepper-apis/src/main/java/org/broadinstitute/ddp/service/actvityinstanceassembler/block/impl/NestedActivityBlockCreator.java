package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.instance.NestedActivityBlock;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;

/**
 * Creates {@link NestedActivityBlock}
 * // TODO ??? is it needs to add summaries?
 */
public class NestedActivityBlockCreator extends ElementCreator {

    public NestedActivityBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public NestedActivityBlock createNestedActivityBlock(NestedActivityBlockDef nestedActivityBlockDef) {
        return new NestedActivityBlock(
                nestedActivityBlockDef.getActivityCode(),
                nestedActivityBlockDef.getRenderHint(),
                nestedActivityBlockDef.isAllowMultiple(),
                getTemplateId(nestedActivityBlockDef.getAddButtonTemplate())
        );
    }
}
