package org.broadinstitute.ddp.model.activity.instance.answer;

import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.util.MiscUtil;
import org.hibernate.validator.constraints.Length;

public class SelectedPicklistOption {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @Length(max = 500)
    @SerializedName("detail")
    private String detailText;

    public SelectedPicklistOption(String stableId) {
        this(stableId, null);
    }

    public SelectedPicklistOption(String stableId, String detailText) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.detailText = detailText;
    }

    public String getStableId() {
        return stableId;
    }

    public String getDetailText() {
        return detailText;
    }
}
