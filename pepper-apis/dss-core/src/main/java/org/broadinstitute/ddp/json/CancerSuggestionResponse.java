package org.broadinstitute.ddp.json;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.suggestion.CancerSuggestion;

@Value
@AllArgsConstructor
public class CancerSuggestionResponse {
    @SerializedName("query")
    String query;

    @SerializedName("results")
    List<CancerSuggestion> results;
}
