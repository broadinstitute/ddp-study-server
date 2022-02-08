package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class ActivityInstanceSelectAnswerSubmission {
    @SerializedName("guid")
    private String guid;

    @SerializedName("name")
    private String name;

    public ActivityInstanceSelectAnswerSubmission(String guid, String name) {
        this.guid = guid;
        this.name = name;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
