package org.broadinstitute.ddp.json.errors;

import java.util.List;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;

/**
 * Error response returned when the activity validation fails
 * Usually indicates that a combination of fields is incompatible
 * E.g. the participant's age is too small for the state of CA
 * to be considered the age of majority
 */
public class ActivityValidationError extends ApiError {
    @SerializedName("validationFailures")
    public List<ActivityValidationFailure> validationFailures;

    public ActivityValidationError(
            String errorMessage,
            List<ActivityValidationFailure> validationFailures
    ) {
        super(ErrorCodes.ACTIVITY_VALIDATION, errorMessage);
        this.validationFailures = validationFailures;
    }

    public List<ActivityValidationFailure> getValidationFailures() {
        return validationFailures;
    }

}
