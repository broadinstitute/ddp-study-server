package org.broadinstitute.ddp.model.activity.instance.answer;

import java.io.Serializable;
import javax.validation.constraints.NotBlank;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.broadinstitute.ddp.util.MiscUtil;
import org.hibernate.validator.constraints.Length;

@Value
public class SelectedPicklistOption implements Serializable {

    @NotBlank
    @SerializedName("stableId")
    String stableId;

    @Length(max = 500)
    @SerializedName("detail")
    String detailText;

    transient String parentStableId;
    transient String groupStableId;

    public SelectedPicklistOption(String stableId) {
        this(stableId, null);
    }

    public SelectedPicklistOption(String stableId, String detailText) {
        this(stableId, null, null, detailText);
    }

    public SelectedPicklistOption(String stableId, String parentStableId, String groupStableId, String detailText) {
        this.stableId = MiscUtil.checkNotBlank(stableId, "stableId");
        this.groupStableId = groupStableId;
        this.parentStableId = parentStableId;
        this.detailText = detailText;
    }
}
