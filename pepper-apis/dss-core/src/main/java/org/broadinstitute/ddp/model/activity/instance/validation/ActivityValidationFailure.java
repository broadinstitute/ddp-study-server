package org.broadinstitute.ddp.model.activity.instance.validation;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ActivityValidationFailure {
    @SerializedName("message")
    String errorMessage;

    @SerializedName("stableIds")
    List<String> affectedQuestionStableIds;
}
