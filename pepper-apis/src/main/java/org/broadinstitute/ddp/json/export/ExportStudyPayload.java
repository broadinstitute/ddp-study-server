package org.broadinstitute.ddp.json.export;

import java.util.Date;

import com.google.gson.annotations.SerializedName;

public class ExportStudyPayload {

    public static final String WORKSPACE_NAME = "workspaceName";
    public static final String WORKSPACE_NAMESPACE = "workspaceNamespace";
    public static final String INCLUDE_AFTER_DATE = "includeAfterDate";

    @SerializedName(WORKSPACE_NAMESPACE)
    private String workspaceNamespace;

    @SerializedName(WORKSPACE_NAME)
    private String workspaceName;

    @SerializedName(INCLUDE_AFTER_DATE)
    private Date includeAfterDate;

    /**
     * Instantiate ExportStudyPayload object.
     */
    public ExportStudyPayload(String workspaceNamespace, String workspaceName, Date includeAfterDate) {
        this.workspaceNamespace = workspaceNamespace;
        this.workspaceName = workspaceName;
        this.includeAfterDate = includeAfterDate;
    }

    public String getWorkspaceNamespace() {
        return workspaceNamespace;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public Date getIncludeAfterDate() {
        return includeAfterDate;
    }
}
