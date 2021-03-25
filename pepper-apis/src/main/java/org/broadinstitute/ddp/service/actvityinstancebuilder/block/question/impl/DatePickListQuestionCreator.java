package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DatePicklistQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.util.QuestionUtil;

/**
 * Creates {@link DatePicklistQuestion}
 */
public class DatePickListQuestionCreator extends ElementCreator {

    public DatePickListQuestionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public DatePicklistQuestion createDatePickListQuestion(QuestionCreator questionCreator, DateQuestionDef questionDef) {
        return new DatePicklistQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                QuestionUtil.isReadOnly(context.getFormResponse(), questionDef),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(DateAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                renderTemplateIfDefined(questionDef.getPlaceholderTemplate()),
                questionDef.getPicklistDef().getUseMonthNames(),
                questionDef.getPicklistDef().getStartYear(),
                questionDef.getPicklistDef().getEndYear(),
                questionDef.getPicklistDef().getFirstSelectedYear()
        );
    }
}
