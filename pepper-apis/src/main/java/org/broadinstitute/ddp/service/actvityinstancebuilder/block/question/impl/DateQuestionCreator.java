package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;

import static org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link DateQuestion}
 */
public class DateQuestionCreator extends ElementCreator {

    public DateQuestionCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public DateQuestion createDateQuestion(QuestionCreator questionCreator, DateQuestionDef questionDef) {
        return new DateQuestion(
                questionDef.getStableId(),
                getTemplateId(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTemplateId(questionDef.getTooltipTemplate()),
                getTemplateId(questionDef.getAdditionalInfoHeaderTemplate()),
                getTemplateId(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(DateAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                getTemplateId(questionDef.getPlaceholderTemplate())
        );
    }
}
