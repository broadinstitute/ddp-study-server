package org.broadinstitute.ddp.service.actvityinstancebuilder.form.block.question;

import static org.broadinstitute.ddp.model.activity.types.DateRenderMode.PICKLIST;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.FileQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DecimalQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.EquationQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.ActivityInstanceSelectQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderContext;
import org.broadinstitute.ddp.util.CollectionMiscUtil;

@Slf4j
public class QuestionCreator {

    public Question createQuestion(AIBuilderContext ctx, QuestionDef questionDef) {
        if (canBeCreated(questionDef)) {
            QuestionCreatorHelper creatorHelper = ctx.getAIBuilderFactory().getQuestionCreatorHelper();
            Question question;

            log.debug("Question definition: {}", questionDef);
            switch (questionDef.getQuestionType()) {
                case DATE:
                    question = ((DateQuestionDef) questionDef).getRenderMode() == PICKLIST
                            ?
                            creatorHelper.createDatePickListQuestion(ctx, (DateQuestionDef) questionDef) :
                            creatorHelper.createDateQuestion(ctx, (DateQuestionDef) questionDef);
                    break;
                case BOOLEAN:
                    question = creatorHelper.createBoolQuestion(ctx, (BoolQuestionDef) questionDef);
                    break;
                case TEXT:
                    question = creatorHelper.createTextQuestion(ctx, (TextQuestionDef) questionDef);
                    break;
                case ACTIVITY_INSTANCE_SELECT:
                    question = creatorHelper.createActivityInstanceSelectQuestion(ctx, (ActivityInstanceSelectQuestionDef) questionDef);
                    break;
                case NUMERIC:
                    question = creatorHelper.createNumericQuestion(ctx, (NumericQuestionDef) questionDef);
                    break;
                case DECIMAL:
                    question = creatorHelper.createDecimalQuestion(ctx, (DecimalQuestionDef) questionDef);
                    break;
                case EQUATION:
                    question = creatorHelper.createEquationQuestion(ctx, (EquationQuestionDef) questionDef);
                    break;
                case PICKLIST:
                    question = creatorHelper.createPicklistQuestion(ctx, (PicklistQuestionDef) questionDef);
                    break;
                case MATRIX:
                    question = creatorHelper.createMatrixQuestion(ctx, (MatrixQuestionDef) questionDef);
                    break;
                case AGREEMENT:
                    question = creatorHelper.createAgreementQuestion(ctx, (AgreementQuestionDef) questionDef);
                    break;
                case COMPOSITE:
                    question = creatorHelper.createCompositeQuestion(ctx, (CompositeQuestionDef) questionDef);
                    break;
                case FILE:
                    question = creatorHelper.constructFileQuestion(ctx, (FileQuestionDef) questionDef);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + questionDef.getQuestionType());
            }
            question.setQuestionId(questionDef.getQuestionId());
            question.shouldHideQuestionNumber(questionDef.shouldHideNumber());
            return question;
        }
        return null;
    }

    private boolean canBeCreated(QuestionDef questionDef) {
        return questionDef != null && !questionDef.isDeprecated();
    }

    <T extends Answer> List<Rule<T>> getValidationRules(AIBuilderContext ctx, QuestionDef questionDef) {
        return CollectionMiscUtil.createListFromAnotherList(questionDef.getValidations(),
                (ruleDef) -> ctx.getAIBuilderFactory().getValidationRuleCreator().createRule(ctx, ruleDef));
    }

    <T extends Answer> List<T> getAnswers(AIBuilderContext ctx, String questionStableId) {
        List<T> answers = new ArrayList<>();
        T answer = (T)ctx.getFormResponse().getAnswer(questionStableId);
        if (answer != null) {
            answers.add(answer);
        }
        return answers;
    }
}
