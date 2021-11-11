package org.broadinstitute.ddp.model.activity.instance.validation;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.RuleType;

/**
 * A validation rule that checks numeric integer value is within an optional min/max range, inclusive.
 */
public class IntRangeRule extends Rule<NumericAnswer> {

    @SerializedName("min")
    private Long min;

    @SerializedName("max")
    private Long max;

    public static IntRangeRule of(Long id, String message, String hint, boolean allowSave, Long min, Long max) {
        IntRangeRule rule = IntRangeRule.of(message, hint, allowSave, min, max);
        rule.setId(id);
        return rule;
    }

    public static IntRangeRule of(String message, String hint, boolean allowSave, Long min, Long max) {
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("Must provide a valid range");
        }
        return new IntRangeRule(message, hint, allowSave, min, max);
    }

    private IntRangeRule(String message, String hint, boolean allowSave, Long min, Long max) {
        super(RuleType.INT_RANGE, message, hint, allowSave);
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean validate(Question<NumericAnswer> question, NumericAnswer answer) {
        if (answer == null) {
            return false;
        } else if (answer.getValue() == null) {
            return true;
        } else if (answer.getNumericType() == NumericType.INTEGER) {
            Long value = ((NumericIntegerAnswer) answer).getValue();
            return (min == null || min <= value) && (max == null || value <= max);
        } else {
            return false;
        }
    }

    public Long getMin() {
        return min;
    }

    public Long getMax() {
        return max;
    }
}
