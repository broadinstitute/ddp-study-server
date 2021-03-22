package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.instance.NestedActivityBlock;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;

/**
 * Creates {@link NestedActivityBlock}
 * // TODO ??? is it needs to add summaries?
 */
public class NestedActivityBlockCreator extends ElementCreator {

    public NestedActivityBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public NestedActivityBlock createNestedActivityBlock(NestedActivityBlockDef nestedActivityBlockDef) {
        NestedActivityBlock nestedActivityBlock = constructNestedActivityBlock(nestedActivityBlockDef);
        render(nestedActivityBlock, nestedActivityBlockDef);
        return nestedActivityBlock;
    }

    private NestedActivityBlock constructNestedActivityBlock(NestedActivityBlockDef nestedActivityBlockDef) {
        return new NestedActivityBlock(
                nestedActivityBlockDef.getActivityCode(),
                nestedActivityBlockDef.getRenderHint(),
                nestedActivityBlockDef.isAllowMultiple(),
                nestedActivityBlockDef.getAddButtonTemplate() != null
                        ? nestedActivityBlockDef.getAddButtonTemplate().getTemplateId() : null
        );
    }

    private void render(NestedActivityBlock nestedActivityBlock, NestedActivityBlockDef nestedActivityBlockDef) {
        renderTemplate(nestedActivityBlockDef.getAddButtonTemplate(),
                nestedActivityBlockDef.getAddButtonTemplate().getTemplateId(), nestedActivityBlock, context);
    }
}
