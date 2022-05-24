package org.broadinstitute.ddp.model.activity.instance;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.broadinstitute.ddp.util.MiscUtil;

@Data
public final class ConsentElection {
    @NotBlank
    @SerializedName("stableId")
    private final String stableId;

    @SerializedName("selected")
    private Boolean selected;

    private final transient String selectedExpr;

    public ConsentElection(String stableId, String selectedExpr) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.selectedExpr = MiscUtil.checkNotBlank(selectedExpr, "selectedExpr");
    }
}
