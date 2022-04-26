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
    public boolean validate(final Question question, final TextAnswer answer) {
        return answer == null || answer.getValue() == null || TransactionWrapper.withTxn(handle -> {
            int answerCount = handle.attach(AnswerSql.class).findAllTextAnswersCountByQuestionIdAndTextAnswer(
                    question.getQuestionId(), answer.getValue());

            return (!allowSave || answerCount <= 1) && (allowSave || answerCount <= 0);
        });
    }
}
