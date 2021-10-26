package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import javax.validation.constraints.NotNull;

public class DynamicSelectAnswer extends Answer<String> {

    @NotNull
    @SerializedName("value")
    protected String value;

    @JdbiConstructor
    public DynamicSelectAnswer(Long answerId, String questionStableId, String answerGuid, String value) {
        super(QuestionType.DYNAMIC_SELECT, answerId, questionStableId, answerGuid);
        this.value = value;
    }

    public DynamicSelectAnswer(Long answerId, String questionStableId, String answerGuid, String value, String actInstanceGuid) {
        super(QuestionType.DYNAMIC_SELECT, answerId, questionStableId, answerGuid, actInstanceGuid);
        this.value = value;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean isEmpty() {
        // Does whitespace count?
        return value == null || value.isEmpty();
    }
}

