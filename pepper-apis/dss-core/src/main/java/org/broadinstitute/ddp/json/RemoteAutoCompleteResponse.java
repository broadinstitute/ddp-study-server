package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.instance.question.PicklistOption;

import java.util.List;

@Value
@AllArgsConstructor
public class RemoteAutoCompleteResponse {
    @SerializedName("query")
    String query;
    
    @SerializedName("results")
    List<PicklistOption> results;
}
