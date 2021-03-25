package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;


import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.instance.ConditionalBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.FormBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;

/**
 * Creates {@link ConditionalBlock}
 */
public class ConditionalBlockCreator extends ElementCreator {

    public ConditionalBlockCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public ConditionalBlock createConditionalBlock(ConditionalBlockDef conditionalBlockDef) {
        var conditionalBlock = constructConditionalBlock(conditionalBlockDef);
        addChildren(conditionalBlock, conditionalBlockDef);
        return conditionalBlock;
    }

    private ConditionalBlock constructConditionalBlock(ConditionalBlockDef conditionalBlockDef) {
        return new ConditionalBlock(
                new QuestionCreator(context).createQuestion(conditionalBlockDef.getControl())
        );
    }

    private void addChildren(ConditionalBlock conditionalBlock, ConditionalBlockDef conditionalBlockDef) {
        var formBlockCreator = new FormBlockCreator(context);
        if (conditionalBlockDef.getNested() != null) {
            conditionalBlockDef.getNested().forEach(b -> conditionalBlock.getNested().add(formBlockCreator.createBlock(b)));
        }
    }
}
