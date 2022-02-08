package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ActivityInstanceSelectAnswersResponse {
    @SerializedName("results")
    private List<ActivityInstanceSelectAnswerSubmission> results;

    public ActivityInstanceSelectAnswersResponse(List<ActivityInstanceSelectAnswerSubmission> results) {
        this.results = results;
    }

    public List<ActivityInstanceSelectAnswerSubmission> getResults() {
        return results;
    }
}
