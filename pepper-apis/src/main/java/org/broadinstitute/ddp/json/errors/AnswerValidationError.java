package org.broadinstitute.ddp.json.errors;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public class AnswerValidationError extends ApiError {

    @SerializedName("violation")
    private Violation violation;

    public AnswerValidationError(String message, String stableId, RuleType rule,
                                 String violationMessage) {
        super(ErrorCodes.ANSWER_VALIDATION, message);
        this.violation = new Violation(stableId, rule, violationMessage);
    }

    public Violation getViolation() {
        return violation;
    }

    public static class Violation {

        @SerializedName("stableId")
        private String stableId;
        @SerializedName("rule")
        private RuleType rule;
        @SerializedName("message")
        private String message;

        /**
         * Instantiate Violateion object.
         */
        public Violation(String stableId, RuleType rule, String message) {
            this.stableId = stableId;
            this.rule = rule;
            this.message = message;
        }

        public String getStableId() {
            return stableId;
        }

        public RuleType getRule() {
            return rule;
        }

        public String getMessage() {
            return message;
        }
    }
}
