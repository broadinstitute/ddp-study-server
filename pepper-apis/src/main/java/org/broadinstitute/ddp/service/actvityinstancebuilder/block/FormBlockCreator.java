package org.broadinstitute.ddp.service.actvityinstancebuilder.block;

import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.AbstractCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;

/**
 * Creates {@link FormBlock}
 */
public class FormBlockCreator extends AbstractCreator {

    private final FormBlockCreatorHelper formBlockCreatorHelper;

    public FormBlockCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
        formBlockCreatorHelper = new FormBlockCreatorHelper(context);
    }

    public FormBlock createBlock(FormBlockDef formBlockDef) {
        var formBlock = constructFormBlock(formBlockDef);
        copyCommonFields(formBlock, formBlockDef);
        return formBlock;
    }

    private FormBlock constructFormBlock(FormBlockDef formBlockDef) {
        switch (formBlockDef.getBlockType()) {
            case GROUP:
                return formBlockCreatorHelper.createGroupBlock((GroupBlockDef) formBlockDef);
            case CONTENT:
                return formBlockCreatorHelper.createContentBlock((ContentBlockDef) formBlockDef);
            case COMPONENT:
                return formBlockCreatorHelper.createComponentBlock((ComponentBlockDef)formBlockDef);
            case ACTIVITY:
                return formBlockCreatorHelper.createNestedActivityBlock((NestedActivityBlockDef) formBlockDef);
            case QUESTION:
                return formBlockCreatorHelper.createQuestionBlock((QuestionBlockDef) formBlockDef);
            case CONDITIONAL:
                return formBlockCreatorHelper.createConditionalBlock((ConditionalBlockDef) formBlockDef);
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
