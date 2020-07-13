package org.broadinstitute.ddp.model.activity.instance.answer;

import java.io.Serializable;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.transformers.Exclude;

public abstract class Answer<T> implements Serializable, Answerable<T> {

    @SerializedName("answerGuid")
    protected String answerGuid;

    @SerializedName("type")
    protected QuestionType type;

    @Exclude
    protected Long answerId;

    @Exclude
    protected String questionStableId;

    @Exclude
    protected String activityInstanceGuid;

    Answer(QuestionType type, Long answerId, String questionStableId, String answerGuid) {
        this.type = type;
        this.answerId = answerId;
        this.questionStableId = questionStableId;
        this.answerGuid = answerGuid;
    }

    Answer(QuestionType type, Long answerId, String questionStableId, String answerGuid, String actInstanceGuid) {
        this.type = type;
        this.answerId = answerId;
        this.questionStableId = questionStableId;
        this.answerGuid = answerGuid;
        this.activityInstanceGuid = actInstanceGuid;
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

    public String getActivityInstanceGuid() {
        return activityInstanceGuid;
    }

    public void setActivityInstanceGuid(String activityInstanceGuid) {
        this.activityInstanceGuid = activityInstanceGuid;
    }
}
