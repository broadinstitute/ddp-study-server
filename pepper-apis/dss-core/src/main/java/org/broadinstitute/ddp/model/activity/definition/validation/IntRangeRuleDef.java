package org.broadinstitute.ddp.model.activity.definition.validation;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public class IntRangeRuleDef extends RuleDef {

    @SerializedName("min")
    private Long min;

    @SerializedName("max")
    private Long max;

    public IntRangeRuleDef(Template hintTemplate, Long min, Long max) {
        super(RuleType.INT_RANGE, hintTemplate);
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("Must provide a valid range");
        }
        this.min = min;
        this.max = max;
    }

    public Long getMin() {
        return min;
    }

    public Long getMax() {
        return max;
    }
}
