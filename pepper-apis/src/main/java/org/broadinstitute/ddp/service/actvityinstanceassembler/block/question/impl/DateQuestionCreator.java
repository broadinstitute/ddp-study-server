package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
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
 * Creates {@link DateQuestion}
 */
public class DateQuestionCreator extends ElementCreator {

    public DateQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public DateQuestion createDateQuestion(QuestionCreator questionCreator, DateQuestionDef questionDef) {
        DateQuestion dateQuestion = constructDateQuestion(questionCreator, questionDef);
        render(dateQuestion, questionDef);
        return dateQuestion;
    }

    private DateQuestion constructDateQuestion(QuestionCreator questionCreator, DateQuestionDef questionDef) {
        return new DateQuestion(
                questionDef.getStableId(),
                getPromptTemplateId(questionDef),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTooltipTemplateId(questionDef),
                getAdditionalInfoHeaderTemplateId(questionDef),
                getAdditionalInfoFooterTemplateId(questionDef),
                questionCreator.getAnswers(DateAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getRenderMode(),
                questionDef.isDisplayCalendar(),
                questionDef.getFields(),
                questionDef.getPlaceholderTemplate() != null
                        ? questionDef.getPlaceholderTemplate().getTemplateId() : null
        );
    }

    private void render(DateQuestion dateQuestion, DateQuestionDef questionDef) {
        renderTemplate(questionDef.getPlaceholderTemplate(), dateQuestion, context);
    }
}
