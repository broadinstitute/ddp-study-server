package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public abstract class Answer<T> implements Answerable<T> {

    @SerializedName("answerGuid")
    protected String answerGuid;

    @SerializedName("type")
    protected QuestionType type;

    protected transient Long answerId;
    protected transient String questionStableId;

    Answer(QuestionType type, Long answerId, String questionStableId, String answerGuid) {
        this.type = type;
        this.answerId = answerId;
        this.questionStableId = questionStableId;
        this.answerGuid = answerGuid;
    }

    public Long getAnswerId() {
        return answerId;
    }

    public void setAnswerId(Long answerId) {
        this.answerId = answerId;
    }

    @Override
    public QuestionType getQuestionType() {
        return type;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public String getAnswerGuid() {
        return answerGuid;
    }

    public void setAnswerGuid(String answerGuid) {
        this.answerGuid = answerGuid;
    }
}
