package org.broadinstitute.ddp.json.workflow;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.workflow.StaticState;

public class WorkflowResponse {

    @SerializedName("next")
    protected String next;

    @SerializedName("studyName")
    protected String studyName;

    @SerializedName("studyGuid")
    protected String studyGuid;

    @SerializedName("redirectUrl")
    protected String redirectUrl;


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

    public WorkflowResponse(String next, String studyName, String studyGuid, String redirectUrl) {
        this.next = next;
        this.studyName = studyName;
        this.studyGuid = studyGuid;
        this.redirectUrl = redirectUrl;
    }

    protected WorkflowResponse() {
        // Use static factories.
    }

    public String getNext() {
        return next;
    }

    public String getStudyName() {
        return studyName;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }
}
