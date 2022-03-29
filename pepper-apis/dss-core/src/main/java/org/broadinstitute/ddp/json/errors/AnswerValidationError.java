package org.broadinstitute.ddp.json.errors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public class AnswerValidationError extends ApiError {
    @SerializedName("violations")
    private List<Violation> violations = new ArrayList<>();

    public AnswerValidationError(String message, Map<String, List<Rule>> failedRulesByQuestion) {
        super(ErrorCodes.ANSWER_VALIDATION, message);
        for (Map.Entry<String, List<Rule>> entry: failedRulesByQuestion.entrySet()) {
            String questionStableId = entry.getKey();
            List<Rule> failedRules = entry.getValue();
            List<ViolatedRule> violatedRules = failedRules.stream().map(rule ->
                    (new ViolatedRule(rule.getRuleType(), rule.getMessage()))).collect(Collectors.toList());
            violations.add(new Violation(questionStableId, violatedRules));
        }
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public static class ViolatedRule {

        @SerializedName("ruleType")
        private RuleType ruleType;
        @SerializedName("message")
        private String message;

        /**
         * Instantiate ViolatedRule object.
         */
        public ViolatedRule(RuleType ruleType, String message) {
            this.ruleType = ruleType;
            this.message = message;
        }

        public RuleType getRuleType() {
            return ruleType;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class Violation {

        @SerializedName("stableId")
        private String stableId;
        @SerializedName("rules")
        private List<ViolatedRule> rules;

        /**
         * Instantiate Violation object.
         */
        public Violation(String stableId, List<ViolatedRule> rules) {
            this.stableId = stableId;
            this.rules = rules;
        }

        public String getStableId() {
            return stableId;
        }

        public List<ViolatedRule> getRules() {
            return rules;
        }

    }
}
