package org.broadinstitute.ddp.model.activity.instance.validation;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AnswerSql;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public class UniqueValueRule extends Rule<TextAnswer> {

    /**
     * Instantiates UniqueValueRule object with id.
     */
    public static UniqueValueRule of(Long id, String message, String hint, boolean allowSave) {
        UniqueValueRule rule = UniqueValueRule.of(message, hint, allowSave);
        rule.setId(id);
        return rule;
    }

    /**
     * Instantiates UniqueValueRule object.
     */
    public static UniqueValueRule of(String message, String hint, boolean allowSave) {
        return new UniqueValueRule(message, hint, allowSave);
    }

    private UniqueValueRule(String message, String hint, boolean allowSave) {
        super(RuleType.UNIQUE_VALUE, message, hint, allowSave);
    }

    @Override
    public boolean validate(Question question, TextAnswer answer) {
        //Take the question , answer and search across All the participant's QA of the study and the same Question.
        if (answer != null && answer.getValue() != null) {
            return TransactionWrapper.withTxn((handle) -> {
                int answerCount =  handle.attach(AnswerSql.class).findAllTextAnswersCountByQuestionIdAndTextAnswer(
                        question.getQuestionId(), answer.getValue());
                int answerCountToCompare = 0;
                if (allowSave) {
                    answerCountToCompare = 1; //existing answer is already saved
                }
                if (answerCount > answerCountToCompare) {
                    return false;
                } else {
                    return true;
                }
            });
        } else {
            return true;
        }
    }
}
