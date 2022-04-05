package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class ActivityInstanceSelectAnswerSubmission {
    @SerializedName("guid")
    private String guid;

    @SerializedName("name")
    private String name;
}
