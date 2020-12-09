package org.broadinstitute.ddp.json.studyemail;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class Attachment {

    @NotEmpty
    @SerializedName("name")
    private String name;

    @NotEmpty
    @SerializedName("guid")
    private String guid;

    public Attachment(@NotEmpty String name, @NotEmpty String guid) {
        this.name = name;
        this.guid = guid;
    }

    public String getName() {
        return name;
    }

    public String getGuid() {
        return guid;
    }
}
