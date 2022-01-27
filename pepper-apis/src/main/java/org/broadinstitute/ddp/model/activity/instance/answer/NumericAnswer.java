package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class NumericAnswer extends Answer<Long> {
    @SerializedName("value")
    private Long value;

    NumericAnswer(Long answerId, String questionStableId, String answerGuid) {
        super(QuestionType.NUMERIC, answerId, questionStableId, answerGuid);
    }

    NumericAnswer(Long answerId, String questionStableId, String answerGuid, String actInstanceGuid) {
        super(QuestionType.NUMERIC, answerId, questionStableId, answerGuid, actInstanceGuid);
    }

    public NumericAnswer(Long answerId, String questionStableId, String answerGuid, Long value) {
        this(answerId, questionStableId, answerGuid);
        this.value = value;
    }

    public NumericAnswer(Long answerId, String questionStableId, String answerGuid, Long value, String actInstanceGuid) {
        this(answerId, questionStableId, answerGuid, actInstanceGuid);
        this.value = value;
    }

    @Override
    public Long getValue() {
        return value;
    }

    @Override
    public void setValue(Long value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }
}
