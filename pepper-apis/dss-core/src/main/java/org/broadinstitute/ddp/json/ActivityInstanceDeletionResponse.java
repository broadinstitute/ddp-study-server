package org.broadinstitute.ddp.json;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.broadinstitute.ddp.json.form.BlockVisibility;

@Data
@NoArgsConstructor
public final class ActivityInstanceDeletionResponse {
    @SerializedName("blockVisibility")
    private List<BlockVisibility> blockVisibilities = new ArrayList<>();
}
