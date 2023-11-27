package org.broadinstitute.ddp.model.activity.instance.validation;

import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.RuleType;

/**
 * A validation rule that checks for "completeness". An answer value is not required, but if user has an answer, the value needs to be
 * "complete". The notion of "completeness" is dependent on the question/answer type.
 *
 * @param <T> any type of answer
 */
public class CompleteRule<T extends Answer> extends Rule<T> {

    public CompleteRule(Long id, String message, String hint, boolean allowSave) {
        super(RuleType.COMPLETE, message, hint, allowSave);
        setId(id);
    }

    public CompleteRule(String message, String hint, boolean allowSave) {
        super(RuleType.COMPLETE, message, hint, allowSave);
    }

    @Override
    public boolean validate(Question<T> question, T answer) {
        if (answer == null) {
            return false;
        }

        if (answer.getQuestionType() == QuestionType.DATE) {
            // "completeness" for date means either value is blank or all specified fields are present.
            DateAnswer ans = (DateAnswer) answer;
            if (ans.getValue() == null) {
                return false;
            } else if (ans.getValue().isBlank()) {
                return true;
            } else {
                return question != null && ((DateQuestion) question).isSpecifiedFieldsPresent(ans);
            }
        }

        return true;
    }
}
