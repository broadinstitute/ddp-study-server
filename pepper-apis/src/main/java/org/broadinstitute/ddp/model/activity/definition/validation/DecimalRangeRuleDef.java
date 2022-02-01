package org.broadinstitute.ddp.model.activity.definition.validation;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

import java.math.BigDecimal;

public class DecimalRangeRuleDef extends RuleDef {

    @SerializedName("min")
    private BigDecimal min;

    @SerializedName("max")
    private BigDecimal max;

    public DecimalRangeRuleDef(Template hintTemplate, BigDecimal min, BigDecimal max) {
        super(RuleType.DECIMAL_RANGE, hintTemplate);
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Must provide a valid range");
        }
        this.min = min;
        this.max = max;
    }

    public BigDecimal getMin() {
        return min;
    }

    public BigDecimal getMax() {
        return max;
    }
}
