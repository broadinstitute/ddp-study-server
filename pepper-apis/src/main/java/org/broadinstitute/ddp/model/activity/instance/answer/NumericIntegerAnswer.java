package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.NumericType;

/**
 * A numeric answer that only supports integers, i.e. whole numbers. Represented using a `long` to cover a wider range of integers.
 */
public class NumericIntegerAnswer extends NumericAnswer<Long> {

    @SerializedName("value")
    private Long value;

    public NumericIntegerAnswer(Long answerId, String questionStableId, String answerGuid, Long value) {
        super(answerId, questionStableId, answerGuid, NumericType.INTEGER);
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
