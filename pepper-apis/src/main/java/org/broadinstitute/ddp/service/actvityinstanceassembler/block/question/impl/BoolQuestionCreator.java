package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.BoolQuestion;
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
 * Creates {@link BoolQuestion}
 */
public class BoolQuestionCreator extends ElementCreator {

    public BoolQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public BoolQuestion createBoolQuestion(QuestionCreator questionCreator, BoolQuestionDef questionDef) {
        BoolQuestion boolQuestion = constructBoolQuestion(questionCreator, questionDef);
        render(boolQuestion, questionDef);
        return boolQuestion;
    }

    private BoolQuestion constructBoolQuestion(QuestionCreator questionCreator, BoolQuestionDef questionDef) {
        return new BoolQuestion(
                questionDef.getStableId(),
                getPromptTemplateId(questionDef),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTooltipTemplateId(questionDef),
                getAdditionalInfoHeaderTemplateId(questionDef),
                getAdditionalInfoFooterTemplateId(questionDef),
                questionCreator.getAnswers(BoolAnswer.class, questionDef.getStableId()),
                questionCreator.getValidationRules(questionDef),
                questionDef.getTrueTemplate() != null ? questionDef.getTrueTemplate().getTemplateId() : null,
                questionDef.getFalseTemplate() != null ? questionDef.getFalseTemplate().getTemplateId() : null
        );
    }

    private void render(BoolQuestion boolQuestion, BoolQuestionDef questionDef) {
        renderTemplate(questionDef.getTrueTemplate(), boolQuestion, context);
        renderTemplate(questionDef.getFalseTemplate(), boolQuestion, context);
    }
}
