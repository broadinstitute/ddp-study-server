package org.broadinstitute.ddp.service.actvityinstancebuilder.block.impl;


import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.instance.QuestionBlock;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;

/**
 * Creates {@link QuestionBlock}
 */
public class QuestionBlockCreator extends ElementCreator {

    public QuestionBlockCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public QuestionBlock createQuestionBlock(QuestionBlockDef questionBlockDef) {
        return new QuestionBlock(new QuestionCreator(context).createQuestion(questionBlockDef.getQuestion()));
    }
}
