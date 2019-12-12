package org.broadinstitute.ddp.json;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.suggestion.DrugSuggestion;

public class DrugSuggestionResponse {
    @SerializedName("query")
    private String query;
    @SerializedName("results")
    private List<DrugSuggestion> results;

    public DrugSuggestionResponse(String query, List<DrugSuggestion> results) {
        this.query = query;
        this.results = results;
    }

    public String getQuery() {
        return query;
    }

    public List<DrugSuggestion> getResults() {
        return results;
    }
}
