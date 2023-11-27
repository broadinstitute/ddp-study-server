package org.broadinstitute.ddp.json.workflow;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.workflow.StateType;

public final class WorkflowStudyRedirectResponse extends WorkflowResponse {

    @NotBlank
    @SerializedName("studyName")
    protected String studyName;

    @SerializedName("studyGuid")
    protected String studyGuid;

    @NotBlank
    @SerializedName("redirectUrl")
    protected String redirectUrl;

    public WorkflowStudyRedirectResponse(String studyName, String studyGuid, String redirectUrl) {
        this.next = StateType.STUDY_REDIRECT.name();;
        this.studyName = studyName;
        this.studyGuid = studyGuid;
        this.redirectUrl = redirectUrl;
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
