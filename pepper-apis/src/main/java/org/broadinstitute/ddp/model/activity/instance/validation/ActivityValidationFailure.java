package org.broadinstitute.ddp.model.activity.instance.validation;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class ActivityValidationFailure {
    @SerializedName("message")
    public String errorMessage;

    @SerializedName("stableIds")
    public List<String> affectedQuestionStableIds;

    public ActivityValidationFailure(String errorMessage, List<String> affectedQuestionStableIds) {
        this.errorMessage = errorMessage;
        this.affectedQuestionStableIds = affectedQuestionStableIds;
    }

    public List<String> getAffectedQuestionStableIds() {
        return affectedQuestionStableIds;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
