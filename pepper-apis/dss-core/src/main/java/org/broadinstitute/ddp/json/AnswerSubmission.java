package org.broadinstitute.ddp.json;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class AnswerSubmission {
    @SerializedName("stableId")
    String questionStableId;

    @SerializedName("answerGuid")
    String answerGuid;

    @SerializedName("value")
    JsonElement value;
}
