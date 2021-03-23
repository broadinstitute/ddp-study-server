package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link BoolQuestion}
 */
public class BoolQuestionCreator extends ElementCreator {

    public BoolQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public BoolQuestion createBoolQuestion(QuestionCreator questionCreator, BoolQuestionDef questionDef) {
        return new BoolQuestion(
                questionDef.getStableId(),
                getTemplateId(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTemplateId(questionDef.getTooltipTemplate()),
                getTemplateId(questionDef.getAdditionalInfoHeaderTemplate()),
                getTemplateId(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(BoolAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                getTemplateId(questionDef.getTrueTemplate()),
                getTemplateId(questionDef.getFalseTemplate())
        );
    }
}
