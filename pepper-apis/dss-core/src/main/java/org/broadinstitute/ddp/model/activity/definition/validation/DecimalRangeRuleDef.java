package org.broadinstitute.ddp.model.activity.definition.validation;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.types.RuleType;

import java.math.BigDecimal;
import java.util.Optional;

public class DecimalRangeRuleDef extends RuleDef {

    @SerializedName("min")
    private DecimalDef min;

    @SerializedName("max")
    private DecimalDef max;

    public DecimalRangeRuleDef(Template hintTemplate, DecimalDef min, DecimalDef max) {
        super(RuleType.DECIMAL_RANGE, hintTemplate);
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Must provide a valid range");
        }
        this.min = min;
        this.max = max;
    }

    public DecimalDef getMin() {
        return min;
    }

    public DecimalDef getMax() {
        return max;
    }

    public BigDecimal getMinAsBigDecimal() {
        return Optional.ofNullable(min).map(DecimalDef::toBigDecimal).orElse(null);
    }

    public BigDecimal getMaxAsBigDecimal() {
        return Optional.ofNullable(max).map(DecimalDef::toBigDecimal).orElse(null);
    }

}
