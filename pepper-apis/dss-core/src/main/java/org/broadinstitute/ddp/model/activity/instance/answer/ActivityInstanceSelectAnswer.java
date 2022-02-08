package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import javax.validation.constraints.NotNull;

public class ActivityInstanceSelectAnswer extends Answer<String> {

    @NotNull
    @SerializedName("value")
    protected String value;

    @JdbiConstructor
    public ActivityInstanceSelectAnswer(Long answerId, String questionStableId, String answerGuid, String value) {
        super(QuestionType.ACTIVITY_INSTANCE_SELECT, answerId, questionStableId, answerGuid);
        this.value = value;
    }

    public ActivityInstanceSelectAnswer(Long answerId, String questionStableId, String answerGuid, String value, String actInstanceGuid) {
        super(QuestionType.ACTIVITY_INSTANCE_SELECT, answerId, questionStableId, answerGuid, actInstanceGuid);
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
        // Can not be empty. If an object of this class exists, it should have a value.
        return false;
    }
}

