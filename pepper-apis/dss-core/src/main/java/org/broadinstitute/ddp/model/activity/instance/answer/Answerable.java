package org.broadinstitute.ddp.model.activity.instance.answer;

import org.broadinstitute.ddp.model.activity.types.QuestionType;

/**
 * Represents something that knows of questions and answers, and can interact with answer values of a certain type.
 *
 * @param <T> the type of answer value
 */
public interface Answerable<T> {

    /**
     * Get the question type that this answer corresponds to.
     *
     * @return the question type
     */
    QuestionType getQuestionType();

    /**
     * Get the actual answer value.
     */
    T getValue();

    /**
     * Save the given answer value.
     *
     * @param value the answer value
     */
    void setValue(T value);

    /**
     * Is this answer empty (e.g. no answer value yet, no options are selected, no text, etc)?
     *
     * @return true if empty
     */
    boolean isEmpty();
}
