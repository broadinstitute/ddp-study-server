package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class BoolAnswer extends Answer<Boolean> {

    @SerializedName("value")
    private Boolean value;

    @JdbiConstructor
    public BoolAnswer(Long answerId, String questionStableId, String answerGuid, Boolean value) {
        super(QuestionType.BOOLEAN, answerId, questionStableId, answerGuid);
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
}

