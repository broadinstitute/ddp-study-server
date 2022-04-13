package org.broadinstitute.ddp.model.activity.instance.answer;

import java.io.Serializable;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.transformers.Exclude;

@Data
@AllArgsConstructor
public abstract class Answer<T> implements Serializable, Answerable<T> {

    @SerializedName("type")
    protected QuestionType questionType;

    @Exclude
    protected Long answerId;

    @Exclude
    protected String questionStableId;

    @SerializedName("answerGuid")
    protected String answerGuid;

    @Exclude
    protected String activityInstanceGuid;

    @SerializedName("response_order")
    protected Integer rowIndex;

    Answer(QuestionType type, Long answerId, String questionStableId, String answerGuid, String activityInstanceGuid) {
        this(type, answerId, questionStableId, answerGuid, activityInstanceGuid, null);
    }

    Answer(QuestionType type, Long answerId, String questionStableId, String answerGuid) {
        this(type, answerId, questionStableId, answerGuid, null);
    }
}
