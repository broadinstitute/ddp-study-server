package org.broadinstitute.ddp.json;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.suggestion.DrugSuggestion;

@Value
@AllArgsConstructor
public class DrugSuggestionResponse {
    @SerializedName("query")
    String query;
    
    @SerializedName("results")
    List<DrugSuggestion> results;
}
