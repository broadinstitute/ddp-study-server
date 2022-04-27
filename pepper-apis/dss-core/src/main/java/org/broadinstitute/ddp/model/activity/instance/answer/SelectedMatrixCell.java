package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Getter
public final class SelectedMatrixCell implements Serializable {
    @NotBlank
    @SerializedName("rowStableId")
    private final String rowStableId;

    @NotBlank
    @SerializedName("optionStableId")
    private final String optionStableId;

    private transient String groupStableId;

    public SelectedMatrixCell(String rowStableId, String optionStableId, String groupStableId) {
        this.rowStableId = MiscUtil.checkNotBlank(rowStableId, "rowStableId");
        this.optionStableId = MiscUtil.checkNotBlank(optionStableId, "optionStableId");
        this.groupStableId = groupStableId;
    }

    public void setGroupStableId(String groupStableId) {
        this.groupStableId = groupStableId;
    }
}
