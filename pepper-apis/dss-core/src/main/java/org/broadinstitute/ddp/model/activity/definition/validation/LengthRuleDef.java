package org.broadinstitute.ddp.model.activity.definition.validation;

import javax.validation.constraints.PositiveOrZero;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.MiscUtil;

public final class LengthRuleDef extends RuleDef {

    @PositiveOrZero
    @SerializedName("minLength")
    private Integer min;

    @PositiveOrZero
    @SerializedName("maxLength")
    private Integer max;

    public LengthRuleDef(Template hintTemplate, Integer min, Integer max) {
        super(RuleType.LENGTH, hintTemplate);
        MiscUtil.checkNullableRange(min, max);
        this.min = min;
        this.max = max;
    }

    public Integer getMin() {
        return min;
    }

    public Integer getMax() {
        return max;
    }
}
