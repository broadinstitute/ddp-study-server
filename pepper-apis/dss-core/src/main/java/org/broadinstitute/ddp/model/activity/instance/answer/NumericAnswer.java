package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public abstract class NumericAnswer<T extends Number> extends Answer<T> {

    @SerializedName("numericType")
    protected NumericType numericType;

    NumericAnswer(Long answerId, String questionStableId, String answerGuid, NumericType numericType) {
        super(QuestionType.NUMERIC, answerId, questionStableId, answerGuid);
        this.numericType = numericType;
    }

    NumericAnswer(Long answerId, String questionStableId, String answerGuid, NumericType numericType, String actInstanceGuid) {
        super(QuestionType.NUMERIC, answerId, questionStableId, answerGuid, actInstanceGuid);
        this.numericType = numericType;
    }

    public NumericType getNumericType() {
        return numericType;
    }
}
