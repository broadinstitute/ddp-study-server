package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.util.QuestionUtil;

/**
 * Creates {@link TextQuestion}
 */
public class TextQuestionCreator extends ElementCreator {

    public TextQuestionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public TextQuestion createTextQuestion(QuestionCreator questionCreator, TextQuestionDef questionDef) {
        return new TextQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                renderTemplateIfDefined(questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                QuestionUtil.isReadOnly(context.getFormResponse(), questionDef),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(TextAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getInputType(),
                questionDef.getSuggestionType(),
                questionDef.getSuggestions(),
                questionDef.isConfirmEntry(),
                renderTemplateIfDefined(questionDef.getConfirmPromptTemplate()),
                renderTemplateIfDefined(questionDef.getMismatchMessageTemplate())
        );
    }
}
