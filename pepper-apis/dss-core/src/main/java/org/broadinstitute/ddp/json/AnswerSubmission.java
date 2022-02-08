package org.broadinstitute.ddp.json;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

public class AnswerSubmission {

    @SerializedName("stableId")
    private String questionStableId;

    @SerializedName("answerGuid")
    private String answerGuid;

    @SerializedName("value")
    private JsonElement value;

    public AnswerSubmission(String questionStableId, String answerGuid, JsonElement value) {
        this.questionStableId = questionStableId;
        this.answerGuid = answerGuid;
        this.value = value;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public String getAnswerGuid() {
        return answerGuid;
    }

    public JsonElement getValue() {
        return value;
    }
}
