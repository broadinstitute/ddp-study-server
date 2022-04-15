package org.broadinstitute.ddp.json;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ActivityInstanceSelectAnswersResponse {
    @SerializedName("results")
    List<ActivityInstanceSelectAnswerSubmission> results;
}
