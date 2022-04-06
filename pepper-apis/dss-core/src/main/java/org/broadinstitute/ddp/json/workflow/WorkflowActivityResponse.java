package org.broadinstitute.ddp.json.workflow;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class WorkflowActivityResponse extends WorkflowResponse {

    @NotBlank
    @SerializedName("activityCode")
    private String activityCode;

    @SerializedName("instanceGuid")
    private String instanceGuid;

    @SerializedName("allowUnauthenticated")
    private boolean allowUnauthenticated;

    public WorkflowActivityResponse(String activityCode, String instanceGuid, boolean allowUnauthenticated) {
        this.next = StateType.ACTIVITY.name();
        this.activityCode = MiscUtil.checkNotBlank(activityCode, "activityCode");
        this.instanceGuid = instanceGuid;
        this.allowUnauthenticated = allowUnauthenticated;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public String getInstanceGuid() {
        return instanceGuid;
    }

    // FIXME isAllow is cumbersome. Downstream users affected by this change. Inform DSM and Metalab when you fix
    public boolean isAllowUnauthenticated() {
        return allowUnauthenticated;
    }
}
