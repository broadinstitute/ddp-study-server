package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;


import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.instance.NestedActivityBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;

/**
 * Creates {@link NestedActivityBlock}
 * // TODO ??? is it needs to add summaries?
 */
public class NestedActivityBlockCreator extends ElementCreator {

    public NestedActivityBlockCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
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
