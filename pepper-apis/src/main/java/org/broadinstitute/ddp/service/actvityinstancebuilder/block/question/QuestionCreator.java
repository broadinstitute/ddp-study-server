package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import static org.broadinstitute.ddp.model.activity.types.DateRenderMode.PICKLIST;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
import org.broadinstitute.ddp.service.actvityinstancebuilder.AbstractCreator;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

/**
 * Creates {@link Question}
 */
public class QuestionCreator extends AbstractCreator {

    private final QuestionCreatorHelper questionCreatorHelper;
    private final ValidationRuleCreator validationRuleCreator;

    public QuestionCreator(ActivityInstanceFromDefinitionBuilder.Context context) {
        super(context);
        validationRuleCreator = new ValidationRuleCreator(context);
        questionCreatorHelper = new QuestionCreatorHelper(context);
    }

    public Question createQuestion(QuestionDef questionDef) {
        switch (questionDef.getQuestionType()) {
            case DATE:
                return ((DateQuestionDef) questionDef).getRenderMode() == PICKLIST
                        ?
                        questionCreatorHelper.createDatePickListQuestion((DateQuestionDef) questionDef) :
                        questionCreatorHelper.createDateQuestion((DateQuestionDef) questionDef);
            case BOOLEAN:
                return questionCreatorHelper.createBoolQuestion((BoolQuestionDef) questionDef);
            case TEXT:
                return questionCreatorHelper.createTextQuestion((TextQuestionDef) questionDef);
            case NUMERIC:
                return questionCreatorHelper.createNumericQuestion((NumericQuestionDef) questionDef);
            case PICKLIST:
                return questionCreatorHelper.createPicklistQuestion((PicklistQuestionDef) questionDef);
            case AGREEMENT:
                return questionCreatorHelper.createAgreementQuestion((AgreementQuestionDef) questionDef);
            case COMPOSITE:
                return questionCreatorHelper.createCompositeQuestion((CompositeQuestionDef) questionDef);
            case FILE:
                return questionCreatorHelper.constructFileQuestion((FileQuestionDef) questionDef);
            default:
                throw new IllegalStateException("Unexpected value: " + questionDef.getQuestionType());
        }
    }

    <T extends Answer> List<Rule<T>> getValidationRules(QuestionDef questionDef) {
        return CollectionMiscUtil.createListFromAnotherList(questionDef.getValidations(),
                (ruleDef) -> validationRuleCreator.createRule(ruleDef));
    }

    <T extends Answer> List<T> getAnswers(Class<T> type, String questionStableId) {
        List<T> answers = new ArrayList<>();
        if (context.getFormResponse().getAnswers() != null) {
            answers = context.getFormResponse().getAnswers().stream()
                    .filter(a -> a.getClass().isAssignableFrom(type) && questionStableId.equals(a.getQuestionStableId()))
                    .map(type::cast)
                    .collect(Collectors.toList());
        }
        return answers;
    }
}
