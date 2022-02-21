package org.broadinstitute.ddp.model.activity.definition.validation;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;
import org.broadinstitute.ddp.model.activity.types.RuleType;

import javax.validation.constraints.NotNull;

@Value
public class ComparisonRuleDef extends RuleDef {
    @NotNull
    @SerializedName("valueStableId")
    String valueStableId;

    @SerializedName("comparison")
    ComparisonType comparison;

    public ComparisonRuleDef(final Template hintTemplate, final String valueStableId, final ComparisonType comparison) {
        super(RuleType.COMPARISON, hintTemplate);
        
        this.valueStableId = valueStableId;
        this.comparison = comparison;
    }
}
