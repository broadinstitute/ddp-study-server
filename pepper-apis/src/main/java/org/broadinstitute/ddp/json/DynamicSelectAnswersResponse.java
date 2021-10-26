package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DynamicSelectAnswersResponse {
    @SerializedName("results")
    private List<DynamicSelectAnswerSubmission> results;

    public DynamicSelectAnswersResponse(List<DynamicSelectAnswerSubmission> results) {
        this.results = results;
    }

    public List<DynamicSelectAnswerSubmission> getResults() {
        return results;
    }
}
