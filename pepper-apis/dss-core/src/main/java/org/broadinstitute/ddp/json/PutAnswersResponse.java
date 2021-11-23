package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;

public class PutAnswersResponse {

    @SerializedName("workflow")
    private WorkflowResponse workflow;

    public PutAnswersResponse(WorkflowResponse workflow) {
        this.workflow = workflow;
    }
}
