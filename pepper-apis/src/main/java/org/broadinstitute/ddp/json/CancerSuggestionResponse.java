package org.broadinstitute.ddp.json;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.suggestion.CancerSuggestion;

public class CancerSuggestionResponse {
    @SerializedName("query")
    private String query;
    @SerializedName("results")
    private List<CancerSuggestion> results;

    public CancerSuggestionResponse(String query, List<CancerSuggestion> results) {
        this.query = query;
        this.results = results;
    }

    public String getQuery() {
        return query;
    }

    public List<CancerSuggestion> getResults() {
        return results;
    }
}
