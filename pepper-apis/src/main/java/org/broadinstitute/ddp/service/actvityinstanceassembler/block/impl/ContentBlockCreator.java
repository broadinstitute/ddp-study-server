package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.instance.ContentBlock;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;

/**
 * Creates {@link ContentBlock}
 */
public class ContentBlockCreator extends ElementCreator {

    public ContentBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public ContentBlock createContentBlock(ContentBlockDef contentBlockDef) {
        ContentBlock contentBlock = constructContentBlock(contentBlockDef);
        render(contentBlock, contentBlockDef);
        return contentBlock;
    }

    private ContentBlock constructContentBlock(ContentBlockDef contentBlockDef) {
        return new ContentBlock(
                contentBlockDef.getTitleTemplateId(),
                contentBlockDef.getBodyTemplateId()
        );
    }

    private void render(ContentBlock contentBlock, ContentBlockDef contentBlockDef) {
        renderTemplate(contentBlockDef.getTitleTemplate(), contentBlock.getTitleTemplateId(), contentBlock, context);
        renderTemplate(contentBlockDef.getBodyTemplate(), contentBlock.getBodyTemplateId(), contentBlock, context);
    }
}
