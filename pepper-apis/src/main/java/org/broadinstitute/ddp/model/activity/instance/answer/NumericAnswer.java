package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class NumericAnswer extends Answer<Long> {
    @SerializedName("value")
    private Long value;

    public NumericAnswer(Long answerId, String questionStableId, String answerGuid, Long value) {
        super(QuestionType.NUMERIC, answerId, questionStableId, answerGuid);
        this.value = value;
    }

    public NumericAnswer(Long answerId, String questionStableId, String answerGuid, Long value, String actInstanceGuid) {
        super(QuestionType.NUMERIC, answerId, questionStableId, answerGuid, actInstanceGuid);
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
