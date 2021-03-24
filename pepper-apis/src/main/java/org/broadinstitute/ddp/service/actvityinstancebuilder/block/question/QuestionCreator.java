package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromActivityDefStoreBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ElementCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl.AgreementQuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl.BoolQuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl.CompositeQuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl.DateQuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl.FileQuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl.NumericQuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl.PicklistQuestionCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.block.question.impl.TextQuestionCreator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Creates {@link Question}
 */
public class QuestionCreator extends ElementCreator {

    public QuestionCreator(ActivityInstanceFromActivityDefStoreBuilder.Context context) {
        super(context);
    }

    public Question createQuestion(QuestionDef questionDef) {
        Question question = constructQuestion(questionDef);
        applyRenderedTemplates(question);
        return question;
    }

    private Question constructQuestion(QuestionDef questionDef) {
        switch (questionDef.getQuestionType()) {
            case DATE:
                return new DateQuestionCreator(context).createDateQuestion(this, (DateQuestionDef) questionDef);
            case BOOLEAN:
                return new BoolQuestionCreator(context).createBoolQuestion(this, (BoolQuestionDef) questionDef);
            case TEXT:
                return new TextQuestionCreator(context).createTextQuestion(this, (TextQuestionDef) questionDef);
            case NUMERIC:
                return new NumericQuestionCreator(context).createNumericQuestion(this, (NumericQuestionDef) questionDef);
            case PICKLIST:
                return new PicklistQuestionCreator(context).createPicklistQuestion(this, (PicklistQuestionDef) questionDef);
            case AGREEMENT:
                return new AgreementQuestionCreator(context).createAgreementQuestion(this, (AgreementQuestionDef) questionDef);
            case COMPOSITE:
                return new CompositeQuestionCreator(context).createCompositeQuestion(this, (CompositeQuestionDef) questionDef);
            case FILE:
                return new FileQuestionCreator(context).constructFileQuestion(this, (FileQuestionDef) questionDef);
            default:
                throw new IllegalStateException("Unexpected value: " + questionDef.getQuestionType());
        }
    }

    public <T extends Answer> List<Rule<T>> getValidationRules(QuestionDef questionDef) {
        ValidationRuleCreator validationRuleCreator = new ValidationRuleCreator(context);
        List<Rule<T>> validationRules = new ArrayList<>();
        if (questionDef.getValidations() != null) {
            questionDef.getValidations().forEach(v -> validationRules.add(validationRuleCreator.createRule(v)));
        }
        return validationRules;
    }

    public <T extends Answer> List<T> getAnswers(Class<T> type, String questionStableId) {
        List<T> answers = new ArrayList<>();
        if (context.getAnswers() != null) {
            answers = context.getAnswers().stream()
                .filter(a -> a.getClass().isAssignableFrom(type) && questionStableId.equals(a.getQuestionStableId()))
                .map(type::cast)
                .collect(Collectors.toList());
        }
        return answers;
    }
}
