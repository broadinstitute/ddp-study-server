package org.broadinstitute.ddp.export.json.structured;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.QuestionType;

public abstract class QuestionRecord {

    @SerializedName("questionType")
    private QuestionType questionType;
    @SerializedName("stableId")
    private String stableId;

    QuestionRecord(QuestionType questionType, String stableId) {
        this.questionType = questionType;
        this.stableId = stableId;
    }
}
