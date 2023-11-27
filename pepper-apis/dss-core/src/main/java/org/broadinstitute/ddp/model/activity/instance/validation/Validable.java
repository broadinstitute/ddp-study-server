package org.broadinstitute.ddp.model.activity.instance.validation;

import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;

/**
 * Represents something that can check and validate answers of a certain type.
 *
 * @param <T> the type of answer this checks
 */
public interface Validable<T extends Answer> {

    /**
     * Get the validation rule type this Validable represents.
     *
     * @return the validation rule type
     */
    RuleType getRuleType();

    /**
     * Get the message that should be used when validation fails.
     *
     * @return the error message
     */
    String getMessage();

    /**
     * Determines if given answer satisfies this validation rule.
     *
     * @param question the question for validation context
     * @param answer   the answer to check
     * @return true if pass, false otherwise
     */
    boolean validate(Question<T> question, T answer);
}
