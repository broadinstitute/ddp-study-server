package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link NumericQuestion}
 */
public class NumericQuestionCreator extends ElementCreator {

    public NumericQuestionCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
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
