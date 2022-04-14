package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

import java.math.BigDecimal;
import java.util.Optional;

public final class DecimalAnswer extends Answer<DecimalDef> {
    @SerializedName("value")
    private DecimalDef value;

    public DecimalAnswer(Long answerId, String questionStableId, String answerGuid, DecimalDef value) {
        super(QuestionType.DECIMAL, answerId, questionStableId, answerGuid);
        this.value = value;
    }

    public DecimalAnswer(Long answerId, String questionStableId, String answerGuid, DecimalDef value, String actInstanceGuid) {
        super(QuestionType.DECIMAL, answerId, questionStableId, answerGuid, actInstanceGuid);
        this.value = value;
    }

    @Override
    public DecimalDef getValue() {
        return value;
    }

    @Override
    public void setValue(DecimalDef value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }

    public BigDecimal getValueAsBigDecimal() {
        return Optional.ofNullable(value).map(DecimalDef::toBigDecimal).orElse(null);
    }
}
