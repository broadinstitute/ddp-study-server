package org.broadinstitute.ddp.service.actvityinstancebuilder.form.block;

import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.TabularBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;

/**
 * Creates {@link FormBlock}
 */
public class FormBlockCreator {

    public FormBlock createBlock(AIBuilderContext ctx, FormBlockDef formBlockDef) {
        var formBlock = constructFormBlock(ctx, formBlockDef);
        copyCommonFields(formBlock, formBlockDef);
        return formBlock;
    }

    private FormBlock constructFormBlock(AIBuilderContext ctx, FormBlockDef formBlockDef) {
        FormBlockCreatorHelper creatorHelper = ctx.getAIBuilderFactory().getFormBlockCreatorHelper();
        switch (formBlockDef.getBlockType()) {
            case GROUP:
                return creatorHelper.createGroupBlock(ctx, (GroupBlockDef) formBlockDef);
            case CONTENT:
                return creatorHelper.createContentBlock(ctx, (ContentBlockDef) formBlockDef);
            case COMPONENT:
                return creatorHelper.createComponentBlock(ctx, (ComponentBlockDef)formBlockDef);
            case ACTIVITY:
                return creatorHelper.createNestedActivityBlock(ctx, (NestedActivityBlockDef) formBlockDef);
            case QUESTION:
                return creatorHelper.createQuestionBlock(ctx, (QuestionBlockDef) formBlockDef);
            case CONDITIONAL:
                return creatorHelper.createConditionalBlock(ctx, (ConditionalBlockDef) formBlockDef);
            case TABULAR:
                return creatorHelper.createTabularBlock(ctx, (TabularBlockDef) formBlockDef);
            default:
                throw new IllegalStateException("Unexpected value: " + formBlockDef.getBlockType());
        }
    }

    private void copyCommonFields(FormBlock formBlock, FormBlockDef formBlockDef) {
        if (formBlock != null) {
            formBlock.setBlockId(formBlockDef.getBlockId());
            formBlock.setGuid(formBlockDef.getBlockGuid());
            formBlock.setShownExpr(formBlockDef.getShownExpr());
            formBlock.setEnabledExpr(formBlockDef.getEnabledExpr());
            formBlock.setColumnSpan(formBlockDef.getColumnSpan());
        }
    }
}
