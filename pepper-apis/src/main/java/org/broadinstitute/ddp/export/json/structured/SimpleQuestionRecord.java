package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public final class SimpleQuestionRecord extends QuestionRecord {

    @SerializedName("answer")
    private Object answer;

    public SimpleQuestionRecord(QuestionType questionType, String stableId, Object answer) {
        super(questionType, stableId);
        this.answer = answer;
    }
}
