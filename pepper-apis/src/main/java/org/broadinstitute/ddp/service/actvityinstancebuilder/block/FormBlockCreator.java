package org.broadinstitute.ddp.service.actvityinstancebuilder.block;


import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl.ComponentBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl.ConditionalBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl.ContentBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl.GroupBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl.NestedActivityBlockCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl.QuestionBlockCreator;

/**
 * Creates {@link FormBlock}
 */
public class FormBlockCreator extends ElementCreator {

    public FormBlockCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public FormBlock createBlock(FormBlockDef formBlockDef) {
        FormBlock formBlock = constructFormBlock(formBlockDef);
        copyCommonFields(formBlock, formBlockDef);
        applyRenderedTemplates(formBlock);
        return formBlock;
    }

    private FormBlock constructFormBlock(FormBlockDef formBlockDef) {
        switch (formBlockDef.getBlockType()) {
            case GROUP:
                return new GroupBlockCreator(context).createGroupBlock((GroupBlockDef) formBlockDef);
            case CONTENT:
                return new ContentBlockCreator(context).createContentBlock((ContentBlockDef) formBlockDef);
            case COMPONENT:
                return new ComponentBlockCreator(context).createComponentBlock((ComponentBlockDef)formBlockDef);
            case ACTIVITY:
                return new NestedActivityBlockCreator(context).createNestedActivityBlock((NestedActivityBlockDef) formBlockDef);
            case QUESTION:
                return new QuestionBlockCreator(context).createQuestionBlock((QuestionBlockDef) formBlockDef);
            case CONDITIONAL:
                return new ConditionalBlockCreator(context).createConditionalBlock((ConditionalBlockDef) formBlockDef);
            default:
                throw new IllegalStateException("Unexpected value: " + formBlockDef.getBlockType());
        }
    }

    private void copyCommonFields(FormBlock formBlock, FormBlockDef formBlockDef) {
        formBlock.setBlockId(formBlockDef.getBlockId());
        formBlock.setGuid(formBlockDef.getBlockGuid());
        formBlock.setShownExpr(formBlockDef.getShownExpr());
    }
}
