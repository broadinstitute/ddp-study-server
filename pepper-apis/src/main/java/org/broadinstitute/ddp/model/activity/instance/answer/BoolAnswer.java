package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class BoolAnswer extends Answer<Boolean> {

    @SerializedName("value")
    private Boolean value;

    @JdbiConstructor
    public BoolAnswer(Long answerId, String questionStableId, String answerGuid, Boolean value, Long languageCodeId) {
        super(QuestionType.BOOLEAN, answerId, questionStableId, answerGuid, languageCodeId);
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

