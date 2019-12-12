package org.broadinstitute.ddp.model.activity.instance.validation;

import javax.validation.constraints.PositiveOrZero;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

/**
 * A validation rule on picklist answers that ensures numbers of selected options is within range.
 */
public class NumOptionsSelectedRule extends Rule<PicklistAnswer> {

    @PositiveOrZero
    @SerializedName("minSelections")
    private Integer min;

    @PositiveOrZero
    @SerializedName("maxSelections")
    private Integer max;

    /**
     * Instantiates NumOptionsSelectedRule object with id.
     */
    public static NumOptionsSelectedRule of(Long id, String message, String hint, boolean allowSave, Integer min, Integer max) {
        NumOptionsSelectedRule rule = NumOptionsSelectedRule.of(message, hint, allowSave, min, max);
        rule.setId(id);
        return rule;
    }

    /**
     * Instantiates NumOptionsSelectedRule object.
     */
    public static NumOptionsSelectedRule of(String message, String hint, boolean allowSave, Integer min, Integer max) {
        MiscUtil.checkNullableRange(min, max);
        return new NumOptionsSelectedRule(message, hint, allowSave, min, max);
    }

    private NumOptionsSelectedRule(String message, String hint, boolean allowSave, Integer min, Integer max) {
        super(RuleType.NUM_OPTIONS_SELECTED, message, hint, allowSave);
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean validate(Question<PicklistAnswer> question, PicklistAnswer answer) {
        if (answer != null && answer.getValue() != null) {
            int count = answer.getValue().size();
            return (min == null || min <= count) && (max == null || count <= max);
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
