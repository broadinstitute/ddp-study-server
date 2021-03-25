package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.util.QuestionUtil;

/**
 * Creates {@link BoolQuestion}
 */
public class BoolQuestionCreator extends ElementCreator {

    public BoolQuestionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public BoolQuestion createBoolQuestion(QuestionCreator questionCreator, BoolQuestionDef questionDef) {
        return new BoolQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                QuestionUtil.isReadOnly(context.getFormResponse(), questionDef),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(BoolAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                renderTemplateIfDefined(questionDef.getTrueTemplate()),
                renderTemplateIfDefined(questionDef.getFalseTemplate())
        );
    }
}
