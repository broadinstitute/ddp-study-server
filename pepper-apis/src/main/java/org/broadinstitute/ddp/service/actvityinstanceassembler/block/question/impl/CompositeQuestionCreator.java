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

import static org.broadinstitute.ddp.service.actvityinstanceassembler.block.question.QuestionUtil.isReadOnly;

/**
 * Creates {@link CompositeQuestion}
 */
public class CompositeQuestionCreator extends ElementCreator {

    public CompositeQuestionCreator(ActivityInstanceAssembleService.Context context) {
        super(context);
    }

    public CompositeQuestion createCompositeQuestion(QuestionCreator questionCreator, CompositeQuestionDef questionDef) {
        return new CompositeQuestion(
                questionDef.getStableId(),
                getTemplateId(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                isReadOnly(context, questionDef),
                getTemplateId(questionDef.getTooltipTemplate()),
                getTemplateId(questionDef.getAdditionalInfoHeaderTemplate()),
                getTemplateId(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getValidationRules(questionDef),
                questionDef.isAllowMultiple(),
                questionDef.isUnwrapOnExport(),
                getTemplateId(questionDef.getAddButtonTemplate()),
                getTemplateId(questionDef.getAdditionalItemTemplate()),
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
}
