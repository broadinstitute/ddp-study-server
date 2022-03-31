package org.broadinstitute.ddp.json.errors;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.broadinstitute.ddp.constants.ErrorCodes;

@Value
public class AnswerExistsError extends ApiError {
    @SerializedName("stableId")
    String stableId;

    public AnswerExistsError(final String message, final String stableId) {
        super(ErrorCodes.ANSWER_EXISTS, message);

        this.stableId = stableId;
    }
}
