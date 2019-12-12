package org.broadinstitute.ddp.json.export;

import com.google.gson.annotations.SerializedName;

public class WorkspaceMetadata {
    public static final String WRITER = "WRITER";
    public static final String OWNER = "OWNER";

    private static final String ACCESS_LEVEL = "accessLevel";
    private static final String WORKSPACE = "workspace";

    @SerializedName(ACCESS_LEVEL)
    private String accessLevel;

    @SerializedName(WORKSPACE)
    private Workspace workspace;

    public WorkspaceMetadata(String accessLevel, Workspace workspace) {
        this.accessLevel = accessLevel;
        this.workspace = workspace;
    }

    /**
     * Given an accessLevel from FC API, check if it's one that allows you to modify a workspace.
     */
    public static boolean hasWriteAccess(String accessLevel) {
        if (accessLevel.equals(WRITER) || accessLevel.equals(OWNER)) {
            return true;
        }
        return false;
    }

    public String getAccessLevel() {
        return accessLevel;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

}
