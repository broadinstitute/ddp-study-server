package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;

import java.util.List;

public class RemoteAutoCompleteResponse {
    @SerializedName("query")
    private String query;
    @SerializedName("results")
    private List<PicklistOption> results;

    public RemoteAutoCompleteResponse(String query, List<PicklistOption> results) {
        this.query = query;
        this.results = results;
    }

    public String getQuery() {
        return query;
    }

    public List<PicklistOption> getResults() {
        return results;
    }

}
