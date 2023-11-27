package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@AllArgsConstructor
public class ActivityInstanceSelectAnswersResponse {
    @SerializedName("results")
    List<ActivityInstanceSelectAnswerSubmission> results;
}
