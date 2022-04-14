package org.broadinstitute.ddp.json;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ActivityInstanceCreationPayload {
    @NotEmpty
    @SerializedName("activityCode")
    String activityCode;

    @SerializedName("parentInstanceGuid")
    String parentInstanceGuid;

    public ActivityInstanceCreationPayload(String activityCode) {
        this(activityCode, null);
    }
}
