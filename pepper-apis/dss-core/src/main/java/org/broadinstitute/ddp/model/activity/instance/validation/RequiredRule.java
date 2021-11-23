package org.broadinstitute.ddp.model.activity.instance.validation;

import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;

/**
 * A validation rule that indicates an answer is required.
 *
 * @param <T> any type of answer
 */
public class RequiredRule<T extends Answer> extends Rule<T> {

    public RequiredRule(Long id, String hint, String message, boolean allowSave) {
        super(RuleType.REQUIRED, message, hint, allowSave);
        setId(id);
    }

    public RequiredRule(String message, String hint, boolean allowSave) {
        super(RuleType.REQUIRED, message, hint, allowSave);
    }

    @Override
    public boolean validate(Question<T> question, T answer) {
        if (answer == null || answer.getValue() == null) {
            return false;
        }

        if (answer.getQuestionType() == QuestionType.PICKLIST) {
            return ((PicklistAnswer) answer).getValue().size() > 0;
        }

        if (answer.getQuestionType() == QuestionType.TEXT) {
            return !((TextAnswer) answer).getValue().isBlank();
        }

        if (answer.getQuestionType() == QuestionType.DATE) {
            return question != null && ((DateQuestion) question).isSpecifiedFieldsPresent((DateAnswer) answer);
        }

        return true;
    }
}
