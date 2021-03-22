package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionCreator;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getAdditionalInfoFooterTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getAdditionalInfoHeaderTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getPromptTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getTooltipTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link TextQuestion}
 */
public class TextQuestionCreator extends ElementCreator {

    public TextQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public TextQuestion createTextQuestion(QuestionCreator questionCreator, TextQuestionDef questionDef) {
        TextQuestion textQuestion = constructTextQuestion(questionCreator, questionDef);
        render(textQuestion, questionDef);
        return textQuestion;
    }

    private TextQuestion constructTextQuestion(QuestionCreator questionCreator, TextQuestionDef questionDef) {
        return new TextQuestion(
                questionDef.getStableId(),
                getPromptTemplateId(questionDef),
                questionDef.getPlaceholderTemplate() != null
                        ? questionDef.getPlaceholderTemplate().getTemplateId() : null,
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTooltipTemplateId(questionDef),
                getAdditionalInfoHeaderTemplateId(questionDef),
                getAdditionalInfoFooterTemplateId(questionDef),
                questionCreator.getAnswers(TextAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getInputType(),
                questionDef.getSuggestionType(),
                questionDef.getSuggestions(),
                questionDef.isConfirmEntry(),
                questionDef.getConfirmPromptTemplate() != null
                        ? questionDef.getConfirmPromptTemplate().getTemplateId() : null,
                questionDef.getMismatchMessageTemplate() != null
                        ? questionDef.getMismatchMessageTemplate().getTemplateId() : null
        );
    }

    private void render(TextQuestion textQuestion, TextQuestionDef questionDef) {
        renderTemplate(questionDef.getPlaceholderTemplate(), textQuestion, context);
        renderTemplate(questionDef.getConfirmPromptTemplate(), textQuestion, context);
        renderTemplate(questionDef.getMismatchMessageTemplate(), textQuestion, context);
    }
}
