package org.broadinstitute.ddp.model.activity.instance.answer;

import org.broadinstitute.ddp.model.activity.types.QuestionType;

public abstract class NumericAnswer<T extends Number> extends Answer<T> {

    NumericAnswer(Long answerId, String questionStableId, String answerGuid) {
        super(QuestionType.NUMERIC, answerId, questionStableId, answerGuid);
    }

    NumericAnswer(Long answerId, String questionStableId, String answerGuid, String actInstanceGuid) {
        super(QuestionType.NUMERIC, answerId, questionStableId, answerGuid, actInstanceGuid);
    }
}
