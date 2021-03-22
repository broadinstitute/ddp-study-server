package org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.impl;

import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ActivityInstanceAssembleService;
import org.broadinstitute.ddp.service.actvityinstanceassembler.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionCreator;

import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.service.actvityinstanceassembler.RenderTemplateUtil.renderTemplate;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getAdditionalInfoFooterTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getAdditionalInfoHeaderTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getPromptTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.getTooltipTemplateId;
import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link CompositeQuestion}
 */
public class CompositeQuestionCreator extends ElementCreator {

    public CompositeQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public CompositeQuestion createCompositeQuestion(QuestionCreator questionCreator, CompositeQuestionDef questionDef) {
        CompositeQuestion compositeQuestion = constructCompositeQuestion(questionCreator, questionDef);
        render(compositeQuestion, questionDef);
        return compositeQuestion;
    }

    private CompositeQuestion constructCompositeQuestion(QuestionCreator questionCreator, CompositeQuestionDef questionDef) {
        return new CompositeQuestion(
                questionDef.getStableId(),
                getPromptTemplateId(questionDef),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTooltipTemplateId(questionDef),
                getAdditionalInfoHeaderTemplateId(questionDef),
                getAdditionalInfoFooterTemplateId(questionDef),
                questionCreator.getValidationRules(questionDef),
                questionDef.isAllowMultiple(),
                questionDef.isUnwrapOnExport(),
                questionDef.getAddButtonTemplate() != null
                        ? questionDef.getAddButtonTemplate().getTemplateId() : null,
                questionDef.getAdditionalItemTemplate() != null
                        ? questionDef.getAdditionalItemTemplate().getTemplateId() : null,
                getChildQuestions(questionDef),
                questionDef.getChildOrientation(),
                questionCreator.getAnswers(CompositeAnswer.class, questionDef.getStableId())
        );
    }

    private List<Question> getChildQuestions(CompositeQuestionDef questionDef) {
        List<Question> childQuestions = new ArrayList<>();
        QuestionCreator questionCreator = new QuestionCreator(context);
        if (questionDef.getChildren() != null) {
            questionDef.getChildren().forEach(q -> childQuestions.add(questionCreator.createQuestion(q)));
        }
        return childQuestions;
    }

    private void render(CompositeQuestion compositeQuestion, CompositeQuestionDef questionDef) {
        renderTemplate(questionDef.getAddButtonTemplate(), compositeQuestion, context);
        renderTemplate(questionDef.getAdditionalItemTemplate(), compositeQuestion, context);
    }
}
