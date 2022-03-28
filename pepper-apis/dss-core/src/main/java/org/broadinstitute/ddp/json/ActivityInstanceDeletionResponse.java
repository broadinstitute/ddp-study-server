package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.broadinstitute.ddp.json.form.BlockVisibility;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public final class ActivityInstanceDeletionResponse {
    @SerializedName("blockVisibility")
    private List<BlockVisibility> blockVisibilities = new ArrayList<>();
}
