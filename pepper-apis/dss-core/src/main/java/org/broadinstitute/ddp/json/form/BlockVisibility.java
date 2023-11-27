package org.broadinstitute.ddp.json.form;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class BlockVisibility {
    @SerializedName("blockGuid")
    private String guid;

    @SerializedName("shown")
    private Boolean shown;

    @SerializedName("enabled")
    private Boolean enabled;
}
