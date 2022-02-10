package org.broadinstitute.ddp.model.activity.instance.validation;

import javax.validation.constraints.PositiveOrZero;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

/**
 * A validation rule that checks length of a text answer value.
 */
public class LengthRule extends Rule<TextAnswer> {

    @PositiveOrZero
    @SerializedName("minLength")
    private Integer min;

    @PositiveOrZero
    @SerializedName("maxLength")
    private Integer max;

    /**
     * Instantiates LengthRuleObject with id.
     */
    public static LengthRule of(Long id, String message, String hint, boolean allowSave, Integer min, Integer max) {
        LengthRule rule = LengthRule.of(message, hint, allowSave, min, max);
        rule.setId(id);
        return rule;
    }

    /**
     * Instantiates LengthRule object.
     */
    public static LengthRule of(String message, String hint, boolean allowSave, Integer min, Integer max) {
        MiscUtil.checkNullableRange(min, max);
        return new LengthRule(message, hint, allowSave, min, max);
    }

    private LengthRule(String message, String hint, boolean allowSave, Integer min, Integer max) {
        super(RuleType.LENGTH, message, hint, allowSave);
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean validate(Question<TextAnswer> question, TextAnswer answer) {
        if (answer != null && answer.getValue() != null) {
            int len = answer.getValue().length();
            return (min == null || min <= len) && (max == null || len <= max);
        }
        return false;
    }

    public Integer getMin() {
        return min;
    }

    public Integer getMax() {
        return max;
    }
}
