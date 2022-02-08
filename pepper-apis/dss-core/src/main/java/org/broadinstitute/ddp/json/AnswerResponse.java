package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class AnswerResponse {

    @SerializedName("stableId")
    private String questionStableId;
    @SerializedName("answerGuid")
    private String answerGuid;

    public AnswerResponse(String questionStableId, String answerGuid) {
        this.questionStableId = questionStableId;
        this.answerGuid = answerGuid;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public String getAnswerGuid() {
        return answerGuid;
    }
}
