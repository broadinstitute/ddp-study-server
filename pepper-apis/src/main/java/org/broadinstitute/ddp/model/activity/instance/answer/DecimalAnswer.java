package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.math.BigDecimal;

public final class DecimalAnswer extends Answer<BigDecimal> {
    @SerializedName("value")
    private BigDecimal value;

    public DecimalAnswer(Long answerId, String questionStableId, String answerGuid, BigDecimal value) {
        super(QuestionType.DECIMAL, answerId, questionStableId, answerGuid);
        this.value = value;
    }

    public DecimalAnswer(Long answerId, String questionStableId, String answerGuid, BigDecimal value, String actInstanceGuid) {
        super(QuestionType.DECIMAL, answerId, questionStableId, answerGuid, actInstanceGuid);
        this.value = value;
    }

    @Override
    public BigDecimal getValue() {
        return value;
    }

    @Override
    public void setValue(BigDecimal value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }
}
