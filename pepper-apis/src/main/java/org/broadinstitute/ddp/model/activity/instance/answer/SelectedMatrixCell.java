package org.broadinstitute.ddp.model.activity.instance.answer;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.util.MiscUtil;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

public class SelectedMatrixCell implements Serializable {

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

    public String getRowStableId() {
        return rowStableId;
    }

    public String getOptionStableId() {
        return optionStableId;
    }

    public String getGroupStableId() {
        return groupStableId;
    }

    public void setGroupStableId(String groupStableId) {
        this.groupStableId = groupStableId;
    }
}
