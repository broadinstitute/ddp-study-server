package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;

public class DynamicSelectAnswerSubmission {
    @SerializedName("answerGuid")
    private String guid;

    @SerializedName("answerValue")
    private String value;

    public DynamicSelectAnswerSubmission(String guid, String value) {
        this.guid = guid;
        this.value = value;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
