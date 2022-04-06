package org.broadinstitute.ddp.model.activity.instance;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.util.MiscUtil;

public class ConsentElection {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @SerializedName("selected")
    private Boolean selected;

    private transient String selectedExpr;

    public ConsentElection(String stableId, String selectedExpr) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.selectedExpr = MiscUtil.checkNotBlank(selectedExpr, "selectedExpr");
    }

    public String getStableId() {
        return stableId;
    }

    public String getSelectedExpr() {
        return selectedExpr;
    }

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }
}
