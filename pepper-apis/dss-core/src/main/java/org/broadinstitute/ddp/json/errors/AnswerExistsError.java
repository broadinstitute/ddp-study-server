package org.broadinstitute.ddp.json.errors;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.constants.ErrorCodes;

public class AnswerExistsError extends ApiError {

    @SerializedName("stableId")
    private String stableId;

    public AnswerExistsError(String message, String stableId) {
        super(ErrorCodes.ANSWER_EXISTS, message);
        this.stableId = stableId;
    }

    public String getStableId() {
        return stableId;
    }
}
