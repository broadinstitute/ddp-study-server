package org.broadinstitute.ddp.json;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.suggestion.CancerSuggestion;

@Value
@AllArgsConstructor
public class CancerSuggestionResponse {

    // the query string used to generate this result
    @SerializedName("query")
    String query;

    // ordered list of matching results, where items at the start of the list are more likely to
    // be selected by users than items towards the end of the list
    @SerializedName("results")
    List<CancerSuggestion> results;
}
