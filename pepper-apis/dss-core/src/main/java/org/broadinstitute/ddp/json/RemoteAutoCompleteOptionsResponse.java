package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.model.activity.instance.question.PicklistGroup;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;

import java.util.List;

public class RemoteAutoCompleteOptionsResponse {
    @SerializedName("query")
    private String query;
    @SerializedName("results")
    private List<PickListOptions> results;

    public RemoteAutoCompleteOptionsResponse(LString query, List<PicklistOption, PicklistGroup> results) {
        this.query = query;
        this.results = results;
    }

    public String getQuery() {
        return query;
    }

    public List<PicklistOption, PicklistGroup> getResults() {
        return results;
    }
}
