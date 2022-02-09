package org.broadinstitute.ddp.json.form;

import com.google.gson.annotations.SerializedName;

public class BlockVisibility {

    @SerializedName("blockGuid")
    private String guid;
    @SerializedName("shown")
    private Boolean shown;
    @SerializedName("enabled")
    private Boolean enabled;

    public BlockVisibility() {}

    public BlockVisibility(String guid, Boolean shown, Boolean enabled) {
        this.guid = guid;
        this.shown = shown;
        this.enabled = enabled;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
