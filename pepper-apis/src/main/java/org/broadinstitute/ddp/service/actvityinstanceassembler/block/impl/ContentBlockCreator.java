package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.instance.ContentBlock;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;

/**
 * Creates {@link ContentBlock}
 */
public class ContentBlockCreator extends ElementCreator {

    public ContentBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public ContentBlock createContentBlock(ContentBlockDef contentBlockDef) {
        return new ContentBlock(
                getTemplateId(contentBlockDef.getTitleTemplate()),
                getTemplateId(contentBlockDef.getBodyTemplate())
        );
    }
}
