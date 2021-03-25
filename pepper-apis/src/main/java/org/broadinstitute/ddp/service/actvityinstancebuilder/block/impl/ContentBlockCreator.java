package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;


import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.instance.ContentBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;

/**
 * Creates {@link ContentBlock}
 */
public class ContentBlockCreator extends ElementCreator {

    public ContentBlockCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public ContentBlock createContentBlock(ContentBlockDef contentBlockDef) {
        return new ContentBlock(
                renderTemplateIfDefined(contentBlockDef.getTitleTemplate()),
                renderTemplateIfDefined(contentBlockDef.getBodyTemplate())
        );
    }
}
