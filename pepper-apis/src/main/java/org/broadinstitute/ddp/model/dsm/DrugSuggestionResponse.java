package org.broadinstitute.ddp.model.dsm;

import java.util.List;

import com.google.gson.annotations.SerializedName;

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
