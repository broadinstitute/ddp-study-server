package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
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
 * Creates {@link NumericQuestion}
 */
public class NumericQuestionCreator extends ElementCreator {

    public NumericQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public NumericQuestion createNumericQuestion(QuestionCreator questionCreator, NumericQuestionDef questionDef) {
        NumericQuestion numericQuestion = constructNumericQuestion(questionCreator, questionDef);
        render(numericQuestion, questionDef);
        return numericQuestion;
    }

    private NumericQuestion constructNumericQuestion(QuestionCreator questionCreator, NumericQuestionDef questionDef) {
        return new NumericQuestion(
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
                questionCreator.getAnswers(NumericAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getNumericType()
        );
    }

    private void render(NumericQuestion numericQuestion, NumericQuestionDef questionDef) {
        renderTemplate(questionDef.getPlaceholderTemplate(), numericQuestion, context);
    }
}
