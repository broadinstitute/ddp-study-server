package org.broadinstitute.ddp.service.actvityinstanceassembler.block.impl;


import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.instance.QuestionBlock;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionCreator;

/**
 * Creates {@link QuestionBlock}
 */
public class QuestionBlockCreator extends ElementCreator {

    public QuestionBlockCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public QuestionBlock createQuestionBlock(QuestionBlockDef questionBlockDef) {
        return new QuestionBlock(new QuestionCreator(context).createQuestion(questionBlockDef.getQuestion()));
    }


}
