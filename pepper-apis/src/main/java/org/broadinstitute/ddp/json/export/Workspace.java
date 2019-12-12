package org.broadinstitute.ddp.json.export;

import com.google.gson.annotations.SerializedName;

public class Workspace {
    private static final String NAME = "name";
    private static final String NAMESPACE = "namespace";

    @SerializedName(NAME)
    private String name;

    @SerializedName(NAMESPACE)
    private String namespace;

    public Workspace(String name, String namespace) {
        this.name = name;
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }
}
