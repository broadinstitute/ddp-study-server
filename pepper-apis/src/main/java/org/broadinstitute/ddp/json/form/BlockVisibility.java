package org.broadinstitute.ddp.json.form;

import com.google.gson.annotations.SerializedName;

public class BlockVisibility {

    @SerializedName("blockGuid")
    private String guid;
    @SerializedName("shown")
    private Boolean shown;

    public BlockVisibility() {}

    public BlockVisibility(String guid, Boolean shown) {
        this.guid = guid;
        this.shown = shown;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public Boolean getShown() {
        return shown;
    }

    public void setShown(Boolean shown) {
        this.shown = shown;
    }
}
