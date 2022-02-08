package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.json.form.BlockVisibility;

import java.util.ArrayList;
import java.util.List;

public class ActivityInstanceDeletionResponse {

    @SerializedName("blockVisibility")
    private List<BlockVisibility> blockVisibilities = new ArrayList<>();

    public ActivityInstanceDeletionResponse() {}

    public List<BlockVisibility> getBlockVisibilities() {
        return blockVisibilities;
    }

    public void setBlockVisibilities(List<BlockVisibility> blockVisibilities) {
        this.blockVisibilities = blockVisibilities;
    }
}
