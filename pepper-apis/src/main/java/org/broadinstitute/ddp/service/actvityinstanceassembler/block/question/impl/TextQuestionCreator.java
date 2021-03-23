package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link TextQuestion}
 */
public class TextQuestionCreator extends ElementCreator {

    public TextQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public TextQuestion createTextQuestion(QuestionCreator questionCreator, TextQuestionDef questionDef) {
        return new TextQuestion(
                questionDef.getStableId(),
                getTemplateId(questionDef.getPromptTemplate()),
                getTemplateId(questionDef.getPlaceholderTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTemplateId(questionDef.getTooltipTemplate()),
                getTemplateId(questionDef.getAdditionalInfoHeaderTemplate()),
                getTemplateId(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getAnswers(TextAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getInputType(),
                questionDef.getSuggestionType(),
                questionDef.getSuggestions(),
                questionDef.isConfirmEntry(),
                getTemplateId(questionDef.getConfirmPromptTemplate()),
                getTemplateId(questionDef.getMismatchMessageTemplate())
        );
    }
}
