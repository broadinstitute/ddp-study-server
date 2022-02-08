package org.broadinstitute.ddp.model.activity.instance.validation;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;

import java.math.BigDecimal;

/**
 * A validation rule that checks numeric integer value is within an optional min/max range, inclusive.
 */
public class DecimalRangeRule extends Rule<DecimalAnswer> {

    @SerializedName("min")
    private BigDecimal min;

    @SerializedName("max")
    private BigDecimal max;

    public static DecimalRangeRule of(Long id, String message, String hint, boolean allowSave, BigDecimal min, BigDecimal max) {
        DecimalRangeRule rule = DecimalRangeRule.of(message, hint, allowSave, min, max);
        rule.setId(id);
        return rule;
    }

    public static DecimalRangeRule of(String message, String hint, boolean allowSave, BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Must provide a valid range");
        }
        return new DecimalRangeRule(message, hint, allowSave, min, max);
    }

    private DecimalRangeRule(String message, String hint, boolean allowSave, BigDecimal min, BigDecimal max) {
        super(RuleType.DECIMAL_RANGE, message, hint, allowSave);
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean validate(Question<DecimalAnswer> question, DecimalAnswer answer) {
        if (answer == null) {
            return false;
        } else if (answer.getValueAsBigDecimal() == null) {
            return true;
        }

        BigDecimal value = answer.getValueAsBigDecimal();
        return (min == null || min.compareTo(value) <= 0) && (max == null || value.compareTo(max) <= 0);
    }

    public BigDecimal getMin() {
        return min;
    }

    public BigDecimal getMax() {
        return max;
    }
}
