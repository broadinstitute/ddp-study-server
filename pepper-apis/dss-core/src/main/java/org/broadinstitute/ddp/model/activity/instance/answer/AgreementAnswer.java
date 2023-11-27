package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class AgreementAnswer extends Answer<Boolean> {

    @SerializedName("value")
    private Boolean value;

    public AgreementAnswer(Long answerId, String questionStableId, String answerGuid, Boolean value, String actInstanceGuid) {
        super(QuestionType.AGREEMENT, answerId, questionStableId, answerGuid, actInstanceGuid);
        this.value = value;
    }

    public AgreementAnswer(Long answerId, String questionStableId, String answerGuid, Boolean value) {
        super(QuestionType.AGREEMENT, answerId, questionStableId, answerGuid);
        this.value = value;
    }

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public void setValue(Boolean value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }
}
