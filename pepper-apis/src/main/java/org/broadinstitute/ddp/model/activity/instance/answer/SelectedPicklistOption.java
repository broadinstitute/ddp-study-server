package org.broadinstitute.ddp.model.activity.instance.answer;

import java.util.Objects;
import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.util.MiscUtil;
import org.hibernate.validator.constraints.Length;

public class SelectedPicklistOption {

    @NotBlank
    @SerializedName("stableId")
    private String stableId;

    @Length(max = 255)
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SelectedPicklistOption that = (SelectedPicklistOption) o;
        return stableId.equals(that.stableId) &&
                Objects.equals(detailText, that.detailText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stableId, detailText);
    }
}
