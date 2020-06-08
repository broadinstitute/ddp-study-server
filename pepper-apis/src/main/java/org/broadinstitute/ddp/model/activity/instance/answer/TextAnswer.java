package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.hibernate.validator.constraints.Length;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class TextAnswer extends Answer<String> {

    @SerializedName("value")
    @Length(max = 10000)
    private String value;

    @JdbiConstructor
    public TextAnswer(Long answerId, String questionStableId, String answerGuid, String value) {
        super(QuestionType.TEXT, answerId, questionStableId, answerGuid);
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
