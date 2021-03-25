package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.QuestionCreator;
import org.broadinstitute.ddp.util.QuestionUtil;

/**
 * Creates {@link CompositeQuestion}
 */
public class CompositeQuestionCreator extends ElementCreator {

    public CompositeQuestionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
    }

    public CompositeQuestion createCompositeQuestion(QuestionCreator questionCreator, CompositeQuestionDef questionDef) {
        return new CompositeQuestion(
                questionDef.getStableId(),
                renderTemplateIfDefined(questionDef.getPromptTemplate()),
                questionDef.isRestricted(),
                questionDef.isDeprecated(),
                QuestionUtil.isReadOnly(context.getFormResponse(), questionDef),
                renderTemplateIfDefined(questionDef.getTooltipTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoHeaderTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalInfoFooterTemplate()),
                questionCreator.getValidationRules(questionDef),
                questionDef.isAllowMultiple(),
                questionDef.isUnwrapOnExport(),
                renderTemplateIfDefined(questionDef.getAddButtonTemplate()),
                renderTemplateIfDefined(questionDef.getAdditionalItemTemplate()),
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
