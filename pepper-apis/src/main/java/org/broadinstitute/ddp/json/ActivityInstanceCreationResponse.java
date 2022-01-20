package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.json.form.BlockVisibility;

import java.util.ArrayList;
import java.util.List;

public class ActivityInstanceCreationResponse {

    @SerializedName("instanceGuid")
    private String instanceGuid;
    @SerializedName("blockVisibility")
    private List<BlockVisibility> blockVisibilities = new ArrayList<>();

    public ActivityInstanceCreationResponse() {}

    public String getInstanceGuid() {
        return instanceGuid;
    }

    public void setInstanceGuid(String instanceGuid) {
        this.instanceGuid = instanceGuid;
    }

    public List<BlockVisibility> getBlockVisibilities() {
        return blockVisibilities;
    }

    public void setBlockVisibilities(List<BlockVisibility> blockVisibilities) {
        this.blockVisibilities = blockVisibilities;
    }
}
