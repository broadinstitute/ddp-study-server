package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class SimpleQuestionRecord extends QuestionRecord {
    @SerializedName("answer")
    private Object answer;

    public SimpleQuestionRecord(final QuestionType questionType, final String stableId, final Object answer) {
        super(questionType, stableId);
        this.answer = answer;
    }

    public SimpleQuestionRecord(final Answer answer) {
        this(answer.getQuestionType(), answer.getQuestionStableId(), answer.getValue());
    }

    public Object getAnswer() {
        return answer;
    }
}
