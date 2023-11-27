package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;

@Value
@AllArgsConstructor
public class PutAnswersResponse {
    @SerializedName("workflow")
    WorkflowResponse workflow;
}
