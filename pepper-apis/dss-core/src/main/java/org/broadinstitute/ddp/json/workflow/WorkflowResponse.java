package org.broadinstitute.ddp.json.workflow;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.workflow.StaticState;

public class WorkflowResponse {

    @SerializedName("next")
    protected String next;

    public static WorkflowResponse from(StaticState state) {
        WorkflowResponse resp = new WorkflowResponse();
        resp.next = state.getType().name();
        return resp;
    }

    public static WorkflowResponse unknown() {
        WorkflowResponse resp = new WorkflowResponse();
        resp.next = "UNKNOWN";
        return resp;
    }

    protected WorkflowResponse() {
        // Use static factories.
    }

    public String getNext() {
        return next;
    }
}
