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
            List<RuleType> failedRulesTypes = failedRules.stream().map(rule -> rule.getRuleType()).collect(Collectors.toList());
            violations.add(new Violation(questionStableId, failedRulesTypes));
        }
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public static class Violation {

        @SerializedName("stableId")
        private String stableId;
        @SerializedName("rules")
        private List<RuleType> rules;

        /**
         * Instantiate Violation object.
         */
        public Violation(String stableId, List<RuleType> rules) {
            this.stableId = stableId;
            this.rules = rules;
        }

        public String getStableId() {
            return stableId;
        }

        public List<RuleType> getRules() {
            return rules;
        }

    }
}
