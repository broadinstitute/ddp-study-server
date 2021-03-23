package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link NumericQuestion}
 */
public class NumericQuestionCreator extends ElementCreator {

    public NumericQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public NumericQuestion createNumericQuestion(QuestionCreator questionCreator, NumericQuestionDef questionDef) {
        return new NumericQuestion(
                questionDef.getStableId(),
                getTemplateId(questionDef.getPromptTemplate()),
                getTemplateId(questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTemplateId(questionDef.getTooltipTemplate()),
                getTemplateId(questionDef.getAdditionalInfoHeaderTemplate()),
                getTemplateId(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(NumericAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getNumericType()
        );
    }
}
