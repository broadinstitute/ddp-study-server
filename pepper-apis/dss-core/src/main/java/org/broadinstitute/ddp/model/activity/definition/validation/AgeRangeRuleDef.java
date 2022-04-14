package org.broadinstitute.ddp.model.activity.definition.validation;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public final class AgeRangeRuleDef extends RuleDef {

    @SerializedName("minAge")
    private Integer minAge;

    @SerializedName("maxAge")
    private Integer maxAge;


    /**
     * Creates a definition of an age range validation rule.
     */
    public AgeRangeRuleDef(Template hintTemplate, Integer minAge, Integer maxAge) {
        super(RuleType.AGE_RANGE, hintTemplate);
        if (minAge == null && maxAge == null) {
            throw new IllegalArgumentException("Need to set at least one of minAge and maxAge");
        }
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new IllegalArgumentException("minAge cannot be greater than maxAge");
        }
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }
}
