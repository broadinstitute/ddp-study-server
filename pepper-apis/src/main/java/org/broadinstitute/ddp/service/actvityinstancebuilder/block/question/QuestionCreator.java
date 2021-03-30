package org.broadinstitute.ddp.service.actvityinstancebuilder.block.question;

import static org.broadinstitute.ddp.model.activity.types.DateRenderMode.PICKLIST;

import java.util.ArrayList;
import java.util.List;

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
import org.broadinstitute.ddp.service.actvityinstancebuilder.Context;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

/**
 * Creates {@link Question}
 */
public class QuestionCreator {

    public Question createQuestion(Context ctx, QuestionDef questionDef) {
        QuestionCreatorHelper creatorHelper = ctx.creators().getQuestionCreatorHelper();
        switch (questionDef.getQuestionType()) {
            case DATE:
                return ((DateQuestionDef) questionDef).getRenderMode() == PICKLIST
                        ?
                        creatorHelper.createDatePickListQuestion(ctx, (DateQuestionDef) questionDef) :
                        creatorHelper.createDateQuestion(ctx, (DateQuestionDef) questionDef);
            case BOOLEAN:
                return creatorHelper.createBoolQuestion(ctx, (BoolQuestionDef) questionDef);
            case TEXT:
                return creatorHelper.createTextQuestion(ctx, (TextQuestionDef) questionDef);
            case NUMERIC:
                return creatorHelper.createNumericQuestion(ctx, (NumericQuestionDef) questionDef);
            case PICKLIST:
                return creatorHelper.createPicklistQuestion(ctx, (PicklistQuestionDef) questionDef);
            case AGREEMENT:
                return creatorHelper.createAgreementQuestion(ctx, (AgreementQuestionDef) questionDef);
            case COMPOSITE:
                return creatorHelper.createCompositeQuestion(ctx, (CompositeQuestionDef) questionDef);
            case FILE:
                return creatorHelper.constructFileQuestion(ctx, (FileQuestionDef) questionDef);
            default:
                throw new IllegalStateException("Unexpected value: " + questionDef.getQuestionType());
        }
    }

    <T extends Answer> List<Rule<T>> getValidationRules(Context ctx, QuestionDef questionDef) {
        return CollectionMiscUtil.createListFromAnotherList(questionDef.getValidations(),
                (ruleDef) -> ctx.creators().getValidationRuleCreator().createRule(ctx, ruleDef));
    }

    <T extends Answer> List<T> getAnswers(Context ctx, String questionStableId) {
        List<T> answers = new ArrayList<>();
        T answer = (T)ctx.getFormResponse().getAnswer(questionStableId);
        if (answer != null) {
            answers.add(answer);
        }
        return answers;
    }
}
